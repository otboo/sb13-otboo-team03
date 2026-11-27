package com.otboo.team03.recommendation.rag;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
}
