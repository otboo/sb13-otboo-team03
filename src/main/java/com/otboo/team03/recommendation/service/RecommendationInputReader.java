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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationInputReader {

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
                weather.getHumidity(), weather.getWindSpeed());
        Integer sensitivity = profileService.getTemperatureSensitivity(userId);
        int effectiveSensitivity = sensitivity != null ? sensitivity : 3;
        List<Clothes> clothes = clothesRepository.findAllByOwner_Id(userId);
        List<UUID> clothesIds = clothes.stream().map(Clothes::getId).toList();
        Map<UUID, List<ClothesAttribute>> attributesByClothesId = clothesIds.isEmpty() ? Map.of()
                : clothesAttributeRepository.findAllByClothes_IdIn(clothesIds).stream()
                        .collect(Collectors.groupingBy(attribute -> attribute.getClothes().getId()));
        Map<UUID, ThicknessLevel> thicknessByClothesId = new HashMap<>();
        Map<UUID, SleeveLength> sleeveByClothesId = new HashMap<>();
        List<LlmRecommendationInput.Item> wardrobe = new ArrayList<>();
        for (Clothes item : clothes) {
            List<ThicknessLevel> thicknessLevels = new ArrayList<>();
            List<SleeveLength> sleeveLengths = new ArrayList<>();
            List<LlmRecommendationInput.Attribute> rawAttributes = new ArrayList<>();
            for (ClothesAttribute attribute : attributesByClothesId.getOrDefault(item.getId(), List.of())) {
                String name = attribute.getDefinition().getName();
                String value = attribute.getValue();
                thicknessLevels.add(attributeAnalyzer.analyze(name, value));
                sleeveLengths.add(sleeveAnalyzer.analyze(name, value));
                rawAttributes.add(new LlmRecommendationInput.Attribute(name, value));
            }
            thicknessByClothesId.put(item.getId(), attributeEstimator.estimateThickness(thicknessLevels));
            sleeveByClothesId.put(item.getId(), attributeEstimator.estimateSleeveLength(sleeveLengths));
            wardrobe.add(new LlmRecommendationInput.Item(item.getId(), item.getName(), item.getType(), rawAttributes));
        }
        return new RecommendationSnapshot(weatherContext, effectiveSensitivity, List.copyOf(clothes),
                attributesByClothesId, thicknessByClothesId, sleeveByClothesId, List.copyOf(wardrobe));
    }
}
