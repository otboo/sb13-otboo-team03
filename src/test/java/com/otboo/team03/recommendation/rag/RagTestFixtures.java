package com.otboo.team03.recommendation.rag;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

final class RagTestFixtures {
    static RecommendationRagProperties properties(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("otboo.recommendation.rag.password", "test-password");
        overrides.forEach((key, value) -> values.put("otboo.recommendation.rag." + key, value));
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate(
                "otboo.recommendation.rag", RecommendationRagProperties.class);
    }
    static StyleReferenceCatalog catalog() throws IOException {
        return new StyleReferenceCatalog(new ClassPathResource("rag/outfit-style-references.tsv"));
    }
    static LlmRecommendationInput input(List<LlmRecommendationInput.Item> items) {
        return new LlmRecommendationInput(new LlmRecommendationInput.WeatherContext(null, null, null, null, null, null, null),
                3, Set.of(), items, List.of());
    }
    static LlmRecommendationInput.Item item(ClothesType type, String name, LlmRecommendationInput.Attribute... attributes) {
        return new LlmRecommendationInput.Item(UUID.randomUUID(), name, type, List.of(attributes));
    }
    static float[] vector(int index) {
        float[] vector = new float[1024]; vector[index] = 1; return vector;
    }
    static LlmRecommendationInput brownTopAndBlackPants() {
        return input(List.of(item(ClothesType.TOP, "초콜릿 컬러 스웨터", attribute("색상", "갈색")),
                item(ClothesType.BOTTOM, "블랙 팬츠", attribute("색상", "검정"), attribute("핏", "와이드"))));
    }
    static LlmRecommendationInput outfitWithConflictingTitles() {
        return input(List.of(item(ClothesType.TOP, "검정 슬림핏 스웨터", attribute("색상", "브라운"), attribute("핏", "오버핏")),
                item(ClothesType.BOTTOM, "흰색 스키니 팬츠", attribute("색상", "블랙"), attribute("핏", "와이드"))));
    }
    static LlmRecommendationInput blueShirtAndNavyPants() {
        return input(List.of(item(ClothesType.TOP, "연하늘색 코튼 셔츠", attribute("색상", "스카이블루")),
                item(ClothesType.BOTTOM, "치노 팬츠", attribute("색상", "남색")),
                item(ClothesType.SHOES, "가죽 로퍼", attribute("색상", "검정"))));
    }
    static LlmRecommendationInput pinkDress() {
        return input(List.of(item(ClothesType.DRESS, "핑크 린넨 원피스", attribute("색상", "분홍"), attribute("두께", "얇음"))));
    }
    static LlmRecommendationInput brownDressWithBlackBag() {
        return input(List.of(item(ClothesType.DRESS, "초콜릿 브라운 니트 원피스", attribute("색상", "초콜릿")),
                item(ClothesType.BAG, "블랙 미니 숄더백", attribute("색상", "블랙"))));
    }
    static LlmRecommendationInput greyDressWithBlackOuter() {
        return input(List.of(item(ClothesType.DRESS, "회색 니트 원피스", attribute("색상", "그레이")),
                item(ClothesType.OUTER, "도시형 퀼팅 패딩 재킷", attribute("색상", "블랙"), attribute("핏", "레귤러"))));
    }
    private static LlmRecommendationInput.Attribute attribute(String name, String value) {
        return new LlmRecommendationInput.Attribute(name, value);
    }
}
