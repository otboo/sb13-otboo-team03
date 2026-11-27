package com.otboo.team03.recommendation.support;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.config.RecommendationLlmProperties;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LlmTestFixtures {
    public static final UUID TOP_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID BOTTOM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID DRESS_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    public static RecommendationLlmProperties properties(boolean enabled) {
        return new RecommendationLlmProperties(enabled, "openai/gpt-oss-120b", 80, 12000, 2048);
    }

    public static LlmRecommendationInput input() {
        return new LlmRecommendationInput(new LlmRecommendationInput.WeatherContext(
                24.0, SkyStatus.CLOUDY, PrecipitationType.RAIN, 80.0, 70.0, 3.0,
                LocalDate.of(2026, 4, 10)), 3, Set.of(TOP_ID), List.of(
                new LlmRecommendationInput.Item(TOP_ID, "시스루 크롭 셔츠", ClothesType.TOP,
                        List.of(new LlmRecommendationInput.Attribute("원단 특징", "비침 있음"),
                                new LlmRecommendationInput.Attribute("핏", "여유로운 실루엣"))),
                new LlmRecommendationInput.Item(BOTTOM_ID, "하이웨이스트 슬랙스", ClothesType.BOTTOM,
                        List.of(new LlmRecommendationInput.Attribute("색상", "검정"))),
                new LlmRecommendationInput.Item(DRESS_ID, "린넨 원피스", ClothesType.DRESS, List.of())), List.of());
    }

    public static LlmOutfitDecision decision() {
        return new LlmOutfitDecision(List.of(TOP_ID, BOTTOM_ID));
    }

    private LlmTestFixtures() { }
}
