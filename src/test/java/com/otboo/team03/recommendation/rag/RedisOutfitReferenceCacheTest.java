package com.otboo.team03.recommendation.rag;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import static com.otboo.team03.recommendation.rag.RagTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
class RedisOutfitReferenceCacheTest {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;
    private final OllamaEmbeddingClient embedding = mock(OllamaEmbeddingClient.class);
    private final PgVectorReferenceStore store = mock(PgVectorReferenceStore.class);

    @BeforeAll static void connect() {
        connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connection.afterPropertiesSet(); redis = new StringRedisTemplate(connection);
    }
    @AfterAll static void close() { if (connection != null) connection.destroy(); }
    @BeforeEach void setup() {
        var keys = redis.keys(RedisOutfitReferenceCache.PREFIX + "*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        when(embedding.embed(anyList())).thenReturn(List.of(vector(0)));
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(new PgVectorReferenceStore.Hit("uniqlo-brown-black", .9)));
    }
    private VectorOutfitReferenceProvider provider(RecommendationRagProperties properties,
            OllamaEmbeddingClient embeddings, PgVectorReferenceStore vectors, StringRedisTemplate template) throws Exception {
        var mapper = JsonMapper.builder().build();
        return new VectorOutfitReferenceProvider(properties, catalog(), embeddings, vectors, new ReferenceCompatibility(), mapper,
                new RedisOutfitReferenceCache(template, mapper, properties));
    }
    @Test void 사전검색은_기다리지_않고_다른_앱이_실제_Redis_결과를_재사용한다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(embedding.embed(anyList())).thenAnswer(call -> { release.await(3, TimeUnit.SECONDS); return List.of(vector(0)); });
        var otherEmbedding = mock(OllamaEmbeddingClient.class);
        var otherStore = mock(PgVectorReferenceStore.class);
        var input = brownTopAndBlackPants();
        var properties = properties(Map.of());
        try (var first = provider(properties, embedding, store, redis);
             var second = provider(properties, otherEmbedding, otherStore, redis)) {
            assertTimeout(Duration.ofSeconds(1), () -> first.prepare(input));
            verify(embedding, timeout(1000)).embed(anyList());
            verifyNoInteractions(store);
            release.countDown();
            var prepared = first.find(input);
            assertThat(prepared).hasSize(1);
            assertThat(second.find(input)).isEqualTo(prepared);
            verifyNoInteractions(otherEmbedding, otherStore);
            String key = redis.keys(RedisOutfitReferenceCache.PREFIX + "*").iterator().next();
            assertThat(key).matches(RedisOutfitReferenceCache.PREFIX + "[a-f0-9]{64}");
            assertThat(redis.opsForValue().get(key)).isEqualTo("[\"uniqlo-brown-black\"]");
            assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 900L);
            redis.expire(key, Duration.ofSeconds(60));
            new RedisOutfitReferenceCache(redis, JsonMapper.builder().build(), properties).find(key.substring(RedisOutfitReferenceCache.PREFIX.length()));
            assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 60L);
        } finally { release.countDown(); }
    }
    @Test void 검색조건_변경이나_만료_후에는_이전_결과를_재사용하지_않는다() throws Exception {
        var input = brownTopAndBlackPants();
        try (var first = provider(properties(Map.of()), embedding, store, redis)) { first.find(input); }
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(new PgVectorReferenceStore.Hit("musinsa-shirt-trousers", .99)));
        try (var changed = provider(properties(Map.of("min-score", .95)), embedding, store, redis)) {
            assertThat(changed.find(input)).extracting(StyleReference.Context::id).containsExactly("musinsa-shirt-trousers");
        }
        redis.keys(RedisOutfitReferenceCache.PREFIX + "*").forEach(key -> redis.expire(key, Duration.ZERO));
        try (var expired = provider(properties(Map.of()), embedding, store, redis)) {
            assertThat(expired.find(input)).extracting(StyleReference.Context::id).containsExactly("musinsa-shirt-trousers");
        }
        verify(embedding, times(3)).embed(anyList());
    }
    @Test void 정상적인_빈_결과도_다른_앱에서_재사용한다() throws Exception {
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt())).thenReturn(List.of());
        var otherEmbedding = mock(OllamaEmbeddingClient.class); var otherStore = mock(PgVectorReferenceStore.class);
        var input = brownTopAndBlackPants();
        try (var first = provider(properties(Map.of()), embedding, store, redis);
             var second = provider(properties(Map.of()), otherEmbedding, otherStore, redis)) {
            assertThat(first.find(input)).isEmpty(); assertThat(second.find(input)).isEmpty();
            verifyNoInteractions(otherEmbedding, otherStore);
        }
    }
    @Test void 손상되거나_현재_자료에_없는_ID는_검색으로_복구한다() throws Exception {
        var input = brownTopAndBlackPants();
        try (var first = provider(properties(Map.of()), embedding, store, redis)) { first.find(input); }
        String key = redis.keys(RedisOutfitReferenceCache.PREFIX + "*").iterator().next();
        for (String value : List.of("invalid-json", "[\"unknown-reference\"]", "[null]")) {
            redis.opsForValue().set(key, value);
            try (var next = provider(properties(Map.of()), embedding, store, redis)) {
                assertThat(next.find(input)).extracting(StyleReference.Context::id).containsExactly("uniqlo-brown-black");
            }
        }
        verify(embedding, times(4)).embed(anyList());
    }
    @Test void 공유캐시_장애가_정상적인_벡터검색_결과를_막지_않는다() throws Exception {
        var unavailable = mock(StringRedisTemplate.class);
        when(unavailable.opsForValue()).thenThrow(new RedisConnectionFailureException("private-details"));
        try (var provider = provider(properties(Map.of()), embedding, store, unavailable)) {
            assertThat(provider.find(brownTopAndBlackPants())).extracting(StyleReference.Context::id).containsExactly("uniqlo-brown-black");
        }
    }
}
