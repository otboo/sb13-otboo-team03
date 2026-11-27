package com.otboo.team03.recommendation.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.algorithm.TemperatureSensitivityPolicy;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

/** 제목·전체 등록 속성·날씨를 한 번의 LLM 요청에 전달한다. */
public record LlmRecommendationInput(
        WeatherContext weather,
        int temperatureSensitivity,
        Set<UUID> previousOutfitIds,
        List<Item> wardrobe,
        List<Set<UUID>> recentCoreOutfitIds
) {
    public LlmRecommendationInput {
        previousOutfitIds = Set.copyOf(previousOutfitIds);
        wardrobe = List.copyOf(wardrobe);
        Map<UUID, ClothesType> types = wardrobe.stream().filter(item -> item.type() != null)
                .collect(Collectors.toMap(Item::clothesId, Item::type, (first, second) -> first));
        recentCoreOutfitIds = recentCoreOutfitIds.stream().map(ids -> ids.stream()
                        .filter(id -> types.get(id) == ClothesType.TOP || types.get(id) == ClothesType.BOTTOM || types.get(id) == ClothesType.DRESS)
                        .collect(Collectors.toUnmodifiableSet()))
                .filter(ids -> validCore(ids, types)).distinct().limit(5).toList();
    }

    private static boolean validCore(Set<UUID> ids, Map<UUID, ClothesType> types) {
        if (ids.size() == 1) return types.get(ids.iterator().next()) == ClothesType.DRESS;
        return ids.size() == 2 && ids.stream().anyMatch(id -> types.get(id) == ClothesType.TOP)
                && ids.stream().anyMatch(id -> types.get(id) == ClothesType.BOTTOM);
    }

    /** 실제 기온·민감도는 유지하고 LLM에 보낼 보정 온도를 공통 정책으로 계산한다. */
    @JsonProperty("adjustedTemperature")
    public Double adjustedTemperature() {
        Double temperature = weather.temperature();
        return temperature == null ? null : TemperatureSensitivityPolicy.adjust(temperature, temperatureSensitivity);
    }

    public record WeatherContext(Double temperature, SkyStatus skyStatus,
                                 PrecipitationType precipitationType, Double precipitationProbability,
                                 Double humidity, Double windSpeed, LocalDate forecastDate) { }

    public record Item(UUID clothesId, String name, ClothesType type, List<Attribute> attributes) {
        public Item {
            attributes = List.copyOf(attributes);
        }
    }

    public record Attribute(String name, String value) { }
}
