package com.otboo.team03.recommendation.service;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesAttribute;
import com.otboo.team03.clothes.repository.ClothesAttributeRepository;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.profile.service.ProfileService;
import com.otboo.team03.recommendation.algorithm.AttributeSemanticAnalyzer;
import com.otboo.team03.recommendation.algorithm.ClothesAttributeEstimator;
import com.otboo.team03.recommendation.algorithm.SleeveLengthAnalyzer;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import com.otboo.team03.recommendation.entity.SleeveLength;
import com.otboo.team03.recommendation.entity.ThicknessLevel;
import com.otboo.team03.recommendation.dto.RecommendationSnapshot;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationInputReader {

    private static final ZoneId FORECAST_ZONE = ZoneId.of("Asia/Seoul");

    private final ClothesRepository clothesRepository;
    private final ClothesAttributeRepository clothesAttributeRepository;
    private final WeatherRepository weatherRepository;
    private final ProfileService profileService;
    private final AttributeSemanticAnalyzer attributeAnalyzer;
    private final ClothesAttributeEstimator attributeEstimator;
    private final SleeveLengthAnalyzer sleeveAnalyzer;

    public RecommendationSnapshot read(UUID weatherId, UUID userId) {
        Weather weather = weatherRepository.findById(weatherId).orElseThrow(WeatherNotFoundException::new);
        var weatherContext = new LlmRecommendationInput.WeatherContext(weather.getTemperature(),
                weather.getSkyStatus(), weather.getPrecipitationType(), weather.getPrecipitationProbability(),
                weather.getHumidity(), weather.getWindSpeed(),
                weather.getForecastAt().atZone(FORECAST_ZONE).toLocalDate());
        Integer sensitivity = profileService.getTemperatureSensitivity(userId);
        int effectiveSensitivity = sensitivity != null ? sensitivity : 3;
        Wardrobe wardrobe = wardrobe(clothesRepository.findAllByOwner_Id(userId));
        List<Clothes> clothes = wardrobe.clothes();
        Map<UUID, List<ClothesAttribute>> attributesByClothesId = wardrobe.attributes();
        Map<UUID, ThicknessLevel> thicknessByClothesId = new HashMap<>();
        Map<UUID, SleeveLength> sleeveByClothesId = new HashMap<>();
        for (Clothes item : clothes) {
            List<ThicknessLevel> thicknessLevels = new ArrayList<>();
            List<SleeveLength> sleeveLengths = new ArrayList<>();
            for (ClothesAttribute attribute : attributesByClothesId.getOrDefault(item.getId(), List.of())) {
                String name = attribute.getDefinition().getName();
                String value = attribute.getValue();
                thicknessLevels.add(attributeAnalyzer.analyze(name, value));
                sleeveLengths.add(sleeveAnalyzer.analyze(name, value));
            }
            thicknessByClothesId.put(item.getId(), attributeEstimator.estimateThickness(thicknessLevels));
            sleeveByClothesId.put(item.getId(), attributeEstimator.estimateSleeveLength(sleeveLengths));
        }
        return new RecommendationSnapshot(weatherContext, effectiveSensitivity, List.copyOf(clothes),
                attributesByClothesId, thicknessByClothesId, sleeveByClothesId, wardrobe.items());
    }

    /** 한도 초과는 속성 조회·DTO 생성 전에 종료한다. 날씨와 프로필은 조회하지 않는다. */
    public Optional<LlmRecommendationInput> readForStyleReferences(UUID userId, int maxClothes) {
        List<Clothes> clothes = clothesRepository.findPage(userId, null, null, null, maxClothes + 1);
        if (clothes.size() > maxClothes) return Optional.empty();
        return Optional.of(new LlmRecommendationInput(
                new LlmRecommendationInput.WeatherContext(null, null, null, null, null, null, null),
                3, Set.of(), wardrobe(clothes).items(), List.of()));
    }

    private Wardrobe wardrobe(List<Clothes> clothes) {
        List<UUID> ids = clothes.stream().map(Clothes::getId).toList();
        Map<UUID, List<ClothesAttribute>> attributes = ids.isEmpty() ? Map.of()
                : clothesAttributeRepository.findAllByClothes_IdIn(ids).stream()
                        .collect(Collectors.groupingBy(attribute -> attribute.getClothes().getId()));
        var items = clothes.stream().map(item -> new LlmRecommendationInput.Item(item.getId(), item.getName(), item.getType(),
                attributes.getOrDefault(item.getId(), List.of()).stream().map(attribute ->
                        new LlmRecommendationInput.Attribute(attribute.getDefinition().getName(), attribute.getValue())).toList())).toList();
        return new Wardrobe(clothes, attributes, items);
    }

    private record Wardrobe(List<Clothes> clothes, Map<UUID, List<ClothesAttribute>> attributes,
                            List<LlmRecommendationInput.Item> items) { }
}
