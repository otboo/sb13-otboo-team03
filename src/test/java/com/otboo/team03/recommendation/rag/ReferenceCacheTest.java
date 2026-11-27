package com.otboo.team03.recommendation.rag;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

import static com.otboo.team03.recommendation.rag.RagTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReferenceCacheTest {
    private final OllamaEmbeddingClient embeddings = mock(OllamaEmbeddingClient.class);
    private final PgVectorReferenceStore store = mock(PgVectorReferenceStore.class);
    private final AtomicLong clock = new AtomicLong();
    @BeforeEach void setup() {
        when(embeddings.embed(anyList())).thenReturn(List.of(vector(0)));
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt()))
                .thenReturn(List.of(new PgVectorReferenceStore.Hit("uniqlo-brown-black", .9)));
    }
    private VectorOutfitReferenceProvider provider(Map<String, Object> overrides) throws Exception {
        return new VectorOutfitReferenceProvider(properties(overrides), catalog(), embeddings, store,
                new ReferenceCompatibility(), JsonMapper.builder().build(), null, clock::get);
    }
    @Test void 날씨_UUID_옷순서_속성순서가_달라도_같은_의미의_옷장은_재사용한다() throws Exception {
        var original = outfitWithConflictingTitles();
        List<LlmRecommendationInput.Item> reversed = new ArrayList<>();
        for (var item : original.wardrobe()) {
            var attrs = new ArrayList<>(item.attributes()); Collections.reverse(attrs);
            reversed.add(new LlmRecommendationInput.Item(UUID.randomUUID(), item.name(), item.type(), attrs));
        }
        Collections.reverse(reversed);
        var changedWeather = new LlmRecommendationInput(new LlmRecommendationInput.WeatherContext(30.0, null, null,
                null, null, null, java.time.LocalDate.of(2027, 7, 1)), 1, Set.of(UUID.randomUUID()), reversed, List.of());
        try (var provider = provider(Map.of())) {
            assertThat(provider.find(original)).isEqualTo(provider.find(changedWeather));
            assertThat(VectorOutfitReferenceProvider.query(original)).isEqualTo(VectorOutfitReferenceProvider.query(changedWeather));
            assertThat(VectorOutfitReferenceProvider.query(original))
                    .contains(original.wardrobe().get(0).name(), "색상=브라운", "핏=와이드")
                    .doesNotContain("forecastDate", original.wardrobe().get(0).clothesId().toString());
            verify(embeddings, times(1)).embed(anyList());
            verify(store, times(1)).search(anyString(), anyString(), any(), anyDouble(), anyInt());
        }
    }
    @ParameterizedTest
    @EnumSource(value = ClothesType.class, names = {"UNDERWEAR", "ETC"})
    void 추천_제외_의상의_추가_변경_삭제는_검색어와_캐시를_바꾸지_않는다(ClothesType excluded) throws Exception {
        var original = brownTopAndBlackPants();
        var items = new ArrayList<>(original.wardrobe());
        items.add(item(excluded, "분홍색 속옷 또는 기타 의상", new LlmRecommendationInput.Attribute("색상", "분홍")));
        var added = input(List.copyOf(items));
        items.set(items.size() - 1, item(excluded, "변경한 파란 의상", new LlmRecommendationInput.Attribute("색상", "파랑")));
        var updated = input(List.copyOf(items));
        var query = VectorOutfitReferenceProvider.query(original);
        assertThat(VectorOutfitReferenceProvider.query(added)).isEqualTo(query);
        assertThat(VectorOutfitReferenceProvider.query(updated)).isEqualTo(query);
        try (var provider = provider(Map.of())) {
            var references = provider.find(original);
            assertThat(references).hasSize(1);
            assertThat(provider.find(added)).isEqualTo(references);
            assertThat(provider.find(updated)).isEqualTo(references);
            assertThat(provider.find(original)).isEqualTo(references);
            verify(embeddings).embed(List.of(query));
            verify(store).search(anyString(), anyString(), any(), anyDouble(), anyInt());
            verifyNoMoreInteractions(embeddings, store);
        }
    }
    @Test void 추천_대상_의상의_제목_속성_종류_추가삭제는_이전_결과를_재사용하지_않는다() throws Exception {
        var input = brownDressWithBlackBag(); var original = input.wardrobe().get(0);
        List<List<LlmRecommendationInput.Item>> changes = List.of(
                input.wardrobe(), List.of(original),
                List.of(item(ClothesType.DRESS, "다른 원피스")),
                List.of(item(ClothesType.DRESS, original.name(), new LlmRecommendationInput.Attribute("색상", "블루"))),
                List.of(item(ClothesType.TOP, original.name(), original.attributes().toArray(LlmRecommendationInput.Attribute[]::new))));
        try (var provider = provider(Map.of())) {
            for (var items : changes) provider.find(input(items));
            verify(embeddings, times(changes.size())).embed(anyList());
        }
    }
    @Test void TTL과_LRU_상한을_지킨다() throws Exception {
        var brown = brownDressWithBlackBag();
        var blue = blueShirtAndNavyPants();
        var grey = greyDressWithBlackOuter();
        try (var provider = provider(Map.of("cache-entries", 2, "cache-ttl", "1s"))) {
            provider.find(brown); provider.find(blue); provider.find(brown);
            provider.find(grey); provider.find(blue);
            verify(embeddings, times(4)).embed(anyList());
            clock.set(TimeUnit.SECONDS.toNanos(2));
            provider.find(blue);
            verify(embeddings, times(5)).embed(anyList());
        }
    }
    @Test void 실패는_캐시하지_않고_다음_호출에서_다시_시도한다() throws Exception {
        when(store.search(anyString(), anyString(), any(), anyDouble(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("private"))
                .thenReturn(List.of(new PgVectorReferenceStore.Hit("uniqlo-brown-black", .9)));
        try (var provider = provider(Map.of())) {
            var input = brownDressWithBlackBag();
            assertThat(provider.find(input)).isEmpty();
            assertThat(provider.find(input)).hasSize(1);
            verify(embeddings, times(2)).embed(anyList());
        }
    }
    @Test void 문자열_구분자가_같아도_실제_의상구조가_다르면_캐시를_공유하지_않는다() throws Exception {
        var one = input(List.of(item(ClothesType.TOP, "t / \nBOTTOM: b")));
        var two = input(List.of(item(ClothesType.TOP, "t"), item(ClothesType.BOTTOM, "b")));
        assertThat(VectorOutfitReferenceProvider.query(one)).isEqualTo(VectorOutfitReferenceProvider.query(two));
        try (var provider = provider(Map.of())) {
            provider.find(one); provider.find(two);
            verify(embeddings, times(2)).embed(anyList());
        }
    }
    @Test void 다른_옷장의_검색이_두개_진행중이면_새_대기열을_만들지_않는다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(embeddings.embed(anyList())).thenAnswer(call -> { release.await(3, TimeUnit.SECONDS); return List.of(vector(0)); });
        try (var provider = provider(Map.of("retrieval-timeout", "100ms"))) {
            provider.find(brownDressWithBlackBag()); provider.find(blueShirtAndNavyPants());
            assertThat(provider.find(greyDressWithBlackOuter())).isEmpty();
            verify(embeddings, times(2)).embed(anyList());
        } finally { release.countDown(); }
    }
}
