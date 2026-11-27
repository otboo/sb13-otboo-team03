package com.otboo.team03.recommendation.dto;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesAttribute;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import com.otboo.team03.recommendation.entity.SleeveLength;
import com.otboo.team03.recommendation.entity.ThicknessLevel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 트랜잭션 안에서 조회한 입력. LLM에는 scalar DTO인 wardrobe와 weather만 전달한다. */
public record RecommendationSnapshot(
        LlmRecommendationInput.WeatherContext weather,
        int sensitivity,
        List<Clothes> clothes,
        Map<UUID, List<ClothesAttribute>> attributesByClothesId,
        Map<UUID, ThicknessLevel> thicknessByClothesId,
        Map<UUID, SleeveLength> sleeveByClothesId,
        List<LlmRecommendationInput.Item> wardrobe
) {
    public LlmRecommendationInput llmInput(Set<UUID> previousOutfitIds, List<Set<UUID>> recentOutfits) {
        return new LlmRecommendationInput(weather, sensitivity, previousOutfitIds, wardrobe, recentOutfits);
    }
}
