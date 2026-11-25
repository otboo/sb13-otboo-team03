package com.otboo.team03.recommendation.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.algorithm.TemperatureSensitivityPolicy;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 개인정보·이미지·선택지 사전 없이 실제 제목과 선택한 모든 속성만 전달한다. */
public record LlmRecommendationInput(
        WeatherContext weather,
        int temperatureSensitivity,
        Set<UUID> previousOutfitIds,
        List<Item> wardrobe
) {
    public LlmRecommendationInput {
        previousOutfitIds = Set.copyOf(previousOutfitIds);
        wardrobe = List.copyOf(wardrobe);
    }

    /** 실제 기온·민감도는 유지하고 LLM에 보낼 보정 온도를 공통 정책으로 계산한다. */
    @JsonProperty("adjustedTemperature")
    public Double adjustedTemperature() {
        Double temperature = weather.temperature();
        return temperature == null ? null : TemperatureSensitivityPolicy.adjust(temperature, temperatureSensitivity);
    }

    public record WeatherContext(Double temperature, SkyStatus skyStatus,
                                 PrecipitationType precipitationType, Double precipitationProbability,
                                 Double humidity, Double windSpeed) { }

    public record Item(UUID clothesId, String name, ClothesType type, List<Attribute> attributes) {
        public Item {
            attributes = List.copyOf(attributes);
        }
    }

    public record Attribute(String name, String value) { }
}
