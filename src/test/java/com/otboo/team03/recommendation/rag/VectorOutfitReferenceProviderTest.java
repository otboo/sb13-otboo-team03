package com.otboo.team03.recommendation.rag;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.otboo.team03.recommendation.rag.RagTestFixtures.*;

@ExtendWith(OutputCaptureExtension.class)
class VectorOutfitReferenceProviderTest {
    private final OllamaEmbeddingClient embedding = mock(OllamaEmbeddingClient.class);
    private final PgVectorReferenceStore store = mock(PgVectorReferenceStore.class);
    private VectorOutfitReferenceProvider provider(Map<String, Object> overrides) throws Exception {
        return new VectorOutfitReferenceProvider(properties(overrides), catalog(), embedding, store,
                new ReferenceCompatibility(), JsonMapper.builder().build(), null);
    }
    @Test void 없는_의상_종류와_색상_충돌을_제외하고_뒤의_후보로_채운다() throws Exception {
        when(embedding.embed(anyList())).thenReturn(List.of(vector(0)));
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt())).thenReturn(List.of(
                new PgVectorReferenceStore.Hit("unknown-reference", .99),
                new PgVectorReferenceStore.Hit("uniqlo-accent-bag", Double.NaN),
                new PgVectorReferenceStore.Hit("uniqlo-accent-bag", .1),
                new PgVectorReferenceStore.Hit("musinsa-shirt-trousers", .9),
                new PgVectorReferenceStore.Hit("musinsa-cropped-curve", .8),
                new PgVectorReferenceStore.Hit("uniqlo-brown-black", .7),
                new PgVectorReferenceStore.Hit("uniqlo-accent-bag", .6)));
        try (var provider = provider(Map.of())) {
            assertThat(provider.find(brownDressWithBlackBag())).extracting(StyleReference.Context::id)
                    .containsExactly("uniqlo-brown-black", "uniqlo-accent-bag");
            assertThat(provider.find(pinkDress())).isEmpty();
        }
    }
    @Test void JSON_직렬화된_참고_문맥의_길이를_제한한다() throws Exception {
        when(embedding.embed(anyList())).thenReturn(List.of(vector(0)));
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(new PgVectorReferenceStore.Hit("uniqlo-brown-black", .9)));
        try (var provider = provider(Map.of("max-context-characters", 100))) {
            assertThat(provider.find(brownDressWithBlackBag())).isEmpty();
        }
    }
    @Test void 저장소_오류를_본문이나_접속정보_로그_없이_복구한다(CapturedOutput output) throws Exception {
        when(embedding.embed(anyList())).thenReturn(List.of(vector(0)));
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("private-password private-wardrobe"));
        try (var provider = provider(Map.of())) { assertThat(provider.find(brownDressWithBlackBag())).isEmpty(); }
        assertThat(output.getAll()).doesNotContain("private-password", "private-wardrobe");
    }
    @Test void 요청_대기만_끝내고_공유_검색은_캐시를_완성한다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(embedding.embed(anyList())).thenAnswer(invocation -> {
            release.await(3, TimeUnit.SECONDS);
            return List.of(vector(0));
        });
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(new PgVectorReferenceStore.Hit("uniqlo-brown-black", .9)));
        try (var provider = provider(Map.of("retrieval-timeout", "100ms"))) {
            var input = brownDressWithBlackBag();
            assertThat(provider.find(input)).isEmpty();
            assertThat(provider.find(input)).isEmpty();
            verifyNoInteractions(store);
            release.countDown();
            assertThat(provider.find(input)).extracting(StyleReference.Context::id).containsExactly("uniqlo-brown-black");
            assertThat(provider.find(input)).hasSize(1);
            verify(embedding, times(1)).embed(anyList());
        } finally { release.countDown(); }
    }
    @Test void 예상하지_못한_코드_오류를_장애로_숨기지_않는다() throws Exception {
        when(embedding.embed(anyList())).thenThrow(new IllegalStateException("bug"));
        try (var provider = provider(Map.of())) {
            assertThatThrownBy(() -> provider.find(brownDressWithBlackBag())).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void 종료된_검색기는_추가_외부호출_없이_빈_문맥으로_복구한다() throws Exception {
        try (var provider = provider(Map.of())) {
            provider.close();
            var input = brownDressWithBlackBag();
            assertThat(provider.find(input)).isEmpty();
            assertThatCode(() -> provider.prepare(input)).doesNotThrowAnyException();
            verifyNoInteractions(embedding, store);
        }
    }
    @Test void 요청_스레드의_인터럽트를_유지하고_공유_검색은_완료한다() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(embedding.embed(anyList())).thenAnswer(call -> {
            started.countDown(); release.await(5, TimeUnit.SECONDS); return List.of(vector(0));
        });
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(new PgVectorReferenceStore.Hit("uniqlo-brown-black", .9)));
        try (var provider = provider(Map.of())) {
            var input = brownDressWithBlackBag();
            provider.prepare(input);
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            Thread.currentThread().interrupt();
            try {
                assertThat(provider.find(input)).isEmpty();
                assertThat(Thread.currentThread().isInterrupted()).isTrue();
            } finally { Thread.interrupted(); }
            release.countDown();
            assertThat(provider.find(input)).hasSize(1);
            verify(embedding).embed(anyList());
        } finally { release.countDown(); }
    }
    @Test void 취소된_검색_작업은_벡터_DB에_접근하지_않는다() throws Exception {
        when(embedding.embed(anyList())).thenAnswer(call -> {
            Thread.currentThread().interrupt(); return List.of(vector(0));
        });
        try (var provider = provider(Map.of())) {
            assertThat(provider.find(brownDressWithBlackBag())).isEmpty();
            verifyNoInteractions(store);
        }
    }
    @Test void 종료하면_대기_요청을_해제하고_늦게_도착한_검색_결과도_버린다() throws Exception {
        var searching = new CountDownLatch(1);
        var waiting = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var lookups = new AtomicInteger();
        var worker = new AtomicReference<Thread>();
        var caller = Executors.newSingleThreadExecutor();
        when(embedding.embed(anyList())).thenReturn(List.of(vector(0)));
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt())).thenAnswer(call -> {
            worker.set(Thread.currentThread()); searching.countDown();
            // 종료 요청을 받더라도 이미 진행 중인 DB 호출이 뒤늦게 반환하는 상황.
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { release.await(5, TimeUnit.SECONDS); }
            return List.of(new PgVectorReferenceStore.Hit("uniqlo-brown-black", .9));
        });
        try (var provider = new VectorOutfitReferenceProvider(properties(Map.of()), catalog(), embedding, store,
                new ReferenceCompatibility(), JsonMapper.builder().build(), null, () -> {
                    if (lookups.incrementAndGet() == 2) waiting.countDown();
                    return 0;
                })) {
            var input = brownDressWithBlackBag();
            provider.prepare(input);
            assertThat(searching.await(1, TimeUnit.SECONDS)).isTrue();
            var result = caller.submit(() -> provider.find(input));
            assertThat(waiting.await(1, TimeUnit.SECONDS)).isTrue();
            provider.close();
            assertThat(result.get(1, TimeUnit.SECONDS)).isEmpty();
            release.countDown();
            worker.get().join(2000);
            assertThat(worker.get().isAlive()).isFalse();
            verify(store).search(anyString(), anyString(), any(), anyDouble(), anyInt());
        } finally { release.countDown(); caller.shutdownNow(); }
    }
}
