package com.otboo.team03.recommendation.rag;

import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public final class VectorOutfitReferenceProvider implements OutfitReferenceProvider, AutoCloseable {
    private final RecommendationRagProperties properties;
    private final StyleReferenceCatalog catalog;
    private final OllamaEmbeddingClient embeddings;
    private final PgVectorReferenceStore store;
    private final ReferenceCompatibility compatibility;
    private final ObjectMapper mapper;
    private final RedisOutfitReferenceCache sharedCache;
    private final LongSupplier ticker;
    private final Map<String, Entry> cache = new LinkedHashMap<>(16, .75f, true);
    private boolean closed;
    // At most two background loads. No waiting queue, including after caller timeouts.
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "outfit-reference-search");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public VectorOutfitReferenceProvider(RecommendationRagProperties properties, StyleReferenceCatalog catalog,
            OllamaEmbeddingClient embeddings, PgVectorReferenceStore store, ReferenceCompatibility compatibility,
            ObjectMapper mapper, RedisOutfitReferenceCache sharedCache) {
        this(properties, catalog, embeddings, store, compatibility, mapper, sharedCache, System::nanoTime);
    }
    VectorOutfitReferenceProvider(RecommendationRagProperties properties, StyleReferenceCatalog catalog,
            OllamaEmbeddingClient embeddings, PgVectorReferenceStore store, ReferenceCompatibility compatibility,
            ObjectMapper mapper, RedisOutfitReferenceCache sharedCache, LongSupplier ticker) {
        this.properties = properties; this.catalog = catalog; this.embeddings = embeddings;
        this.store = store; this.compatibility = compatibility; this.mapper = mapper; this.ticker = ticker;
        this.sharedCache = sharedCache;
    }
    @Override public void prepare(LlmRecommendationInput input) {
        try { getOrStart(input); }
        catch (RejectedExecutionException exception) {
            log.debug("스타일 사전 검색 생략: 종료 또는 작업 한도 초과");
        }
    }
    @Override public List<StyleReference.Context> find(LlmRecommendationInput input) {
        try {
            Entry entry = getOrStart(input);
            return entry.result.get(properties.retrievalTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException | RejectedExecutionException | CancellationException exception) {
            // A caller stops waiting; a shared bounded load may finish for later requests.
            log.debug("스타일 문맥 준비 중/혼잡, 이번 요청은 LLM 단독 추천 사용");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (!(cause instanceof RestClientException || cause instanceof DataAccessException
                    || cause instanceof ReferenceRetrievalException)) {
                if (cause instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("스타일 검색 코드 오류", cause);
            }
            log.warn("스타일 검색 실패, LLM 단독 추천 사용: errorType={}", cause.getClass().getSimpleName());
        }
        return List.of();
    }
    private synchronized Entry getOrStart(LlmRecommendationInput input) {
        if (closed) throw new RejectedExecutionException();
        long now = ticker.getAsLong();
        cache.values().removeIf(entry -> entry.result.isDone() && now - entry.completedAt >= properties.cacheTtl().toNanos());
        String key = key(input);
        Entry present = cache.get(key);
        if (present != null) return present;
        if (cache.size() >= properties.cacheEntries()) {
            var entries = cache.entrySet().iterator();
            boolean evicted = false;
            while (entries.hasNext()) {
                if (entries.next().getValue().result.isDone()) { entries.remove(); evicted = true; break; }
            }
            if (!evicted) throw new RejectedExecutionException();
        }
        Entry entry = new Entry();
        cache.put(key, entry);
        try { workers.execute(() -> load(key, entry, input)); }
        catch (RejectedExecutionException exception) { cache.remove(key, entry); throw exception; }
        return entry;
    }
    private void load(String key, Entry entry, LlmRecommendationInput input) {
        try {
            var cachedIds = sharedCache == null ? java.util.Optional.<List<String>>empty() : sharedCache.find(key);
            List<StyleReference.Context> result = null;
            if (cachedIds.isPresent() && cachedIds.get().stream().allMatch(id -> catalog.get(id) != null)) {
                var restored = select(cachedIds.get(), input);
                if (restored.size() == cachedIds.get().size()) {
                    result = restored;
                    log.debug("스타일 문맥 Redis 캐시 적중: references={}", result.size());
                }
            }
            if (result == null) {
                result = retrieve(input);
                if (sharedCache != null) sharedCache.save(key, result);
                log.debug("스타일 문맥 검색 준비 완료: references={}", result.size());
            }
            synchronized (this) {
                if (closed) { entry.result.cancel(false); return; }
                entry.completedAt = ticker.getAsLong();
                entry.result.complete(result);
            }
        } catch (RuntimeException | Error exception) {
            synchronized (this) { cache.remove(key, entry); entry.result.completeExceptionally(exception); }
            log.warn("스타일 문맥 준비 실패: errorType={}", exception.getClass().getSimpleName());
            if (exception instanceof Error error) throw error;
        }
    }
    private List<StyleReference.Context> retrieve(LlmRecommendationInput input) {
        float[] vector = embeddings.embed(List.of(query(input))).get(0);
        if (Thread.currentThread().isInterrupted()) throw new ReferenceRetrievalException();
        var candidates = store.search(catalog.version(), properties.modelKey(), vector, properties.minScore(), properties.candidates());
        return select(candidates.stream().filter(hit -> Double.isFinite(hit.score()) && hit.score() >= properties.minScore())
                .map(PgVectorReferenceStore.Hit::id).toList(), input);
    }
    private List<StyleReference.Context> select(List<String> ids, LlmRecommendationInput input) {
        List<StyleReference.Context> selected = new ArrayList<>();
        for (String id : ids) {
            StyleReference reference = catalog.get(id);
            if (reference == null) continue;
            var assessment = compatibility.assess(reference, input);
            if (assessment == ReferenceCompatibility.Assessment.CONFLICT) continue;
            selected.add(new StyleReference.Context(reference.id(), reference.source(), reference.url(), reference.summary(), assessment.name()));
            if (mapper.writeValueAsString(Map.of("styleReferences", selected)).length() > properties.maxContextCharacters()) {
                selected.remove(selected.size() - 1); break;
            }
            if (selected.size() == properties.topK()) break;
        }
        return List.copyOf(selected);
    }
    /** Sort semantic fields, retaining duplicate items; IDs, dates and history do not affect style search. */
    private static List<SearchItem> semanticItems(LlmRecommendationInput input) {
        var attributeOrder = Comparator.comparing(LlmRecommendationInput.Attribute::name)
                .thenComparing(LlmRecommendationInput.Attribute::value);
        return input.wardrobe().stream().map(item -> new SearchItem(item.type().name(), item.type().ordinal(), item.name(),
                        item.attributes().stream().sorted(attributeOrder).toList()))
                .sorted(Comparator.comparingInt(SearchItem::order).thenComparing(SearchItem::signature)).toList();
    }
    static String query(LlmRecommendationInput input) {
        return semanticItems(input).stream().map(item -> item.type() + ": " + item.name() + " / "
                + item.attributes().stream().map(a -> a.name() + "=" + a.value()).collect(Collectors.joining(" / ")))
                .collect(Collectors.joining("\n"));
    }
    private String key(LlmRecommendationInput input) {
        // Length-prefix fields to distinguish names containing separators/newlines from additional items.
        String signature = part("style-search-v2") + part(catalog.version()) + part(properties.modelKey())
                + part(properties.candidates() + "/" + properties.topK() + "/" + properties.minScore() + "/" + properties.maxContextCharacters())
                + semanticItems(input).stream().map(SearchItem::signature).map(VectorOutfitReferenceProvider::part).collect(Collectors.joining());
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static String part(String value) { return value.length() + ":" + value; }
    @Override public synchronized void close() {
        closed = true; workers.shutdownNow(); cache.values().forEach(entry -> entry.result.cancel(false)); cache.clear();
    }
    private static final class Entry {
        final CompletableFuture<List<StyleReference.Context>> result = new CompletableFuture<>();
        long completedAt = -1;
    }
    private record SearchItem(String type, int order, String name, List<LlmRecommendationInput.Attribute> attributes) {
        String signature() {
            return part(type) + part(name) + attributes.size() + ":" + attributes.stream()
                    .map(a -> part(a.name()) + part(a.value())).collect(Collectors.joining());
        }
    }
}
