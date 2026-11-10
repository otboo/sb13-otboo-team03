package com.otboo.team03.recommendation.service;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesAttribute;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.clothes.repository.ClothesAttributeRepository;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.feed.dto.response.OotdDto;
import com.otboo.team03.profile.service.ProfileService;
import com.otboo.team03.recommendation.algorithm.BasicOutfitAlgorithm;
import com.otboo.team03.recommendation.algorithm.AttributeSemanticAnalyzer;
import com.otboo.team03.recommendation.algorithm.ClothesAttributeEstimator;
import com.otboo.team03.recommendation.algorithm.SleeveLengthAnalyzer;
import com.otboo.team03.recommendation.entity.SleeveLength;
import com.otboo.team03.recommendation.entity.ThicknessLevel;
import com.otboo.team03.recommendation.dto.RecommendationDto;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendationService {

    private final ClothesRepository clothesRepository;
    private final ClothesAttributeRepository clothesAttributeRepository;
    private final WeatherRepository weatherRepository;
    private final ProfileService profileService;
    private final ClothesService clothesService;
    private final BasicOutfitAlgorithm algorithm;
    private final AttributeSemanticAnalyzer attributeAnalyzer;
    private final ClothesAttributeEstimator attributeEstimator;
    private final SleeveLengthAnalyzer sleeveAnalyzer;

    public RecommendationDto recommend(UUID weatherId, UUID userId) {
        Double temperature = weatherRepository.findById(weatherId)
                .orElseThrow(WeatherNotFoundException::new)
                .getTemperature();

        Integer sensitivity = profileService.getProfile(userId).temperatureSensitivity();
        int effectiveSensitivity = sensitivity != null ? sensitivity : 3;

        List<Clothes> clothes = clothesRepository.findAllByOwner_Id(userId);
        List<Clothes> recommended;
        if (clothes.isEmpty()) {
            recommended = algorithm.recommend(clothes, temperature, effectiveSensitivity);
        } else {
            List<UUID> clothesIds = clothes.stream().map(Clothes::getId).toList();
            Map<UUID, List<ClothesAttribute>> attributesByClothesId = loadAttributesByClothesId(clothesIds);
            Map<UUID, ThicknessLevel> thicknessByClothesId = estimateThicknessByClothesId(clothesIds, attributesByClothesId);
            Map<UUID, SleeveLength> sleeveByClothesId = estimateSleeveByClothesId(clothesIds, attributesByClothesId);
            recommended = algorithm.recommend(clothes, thicknessByClothesId, sleeveByClothesId, temperature, effectiveSensitivity);
        }

        List<OotdDto> ootds = recommended.stream()
                .map(c -> clothesService.findById(c.getId()))
                .map(OotdDto::fromClothesDto)
                .toList();

        return new RecommendationDto(weatherId, userId, ootds);
    }

    private Map<UUID, List<ClothesAttribute>> loadAttributesByClothesId(List<UUID> clothesIds) {
        return clothesAttributeRepository.findAllByClothes_IdIn(clothesIds).stream()
                .collect(Collectors.groupingBy(attribute -> attribute.getClothes().getId()));
    }

    private Map<UUID, ThicknessLevel> estimateThicknessByClothesId(
            List<UUID> clothesIds, Map<UUID, List<ClothesAttribute>> attributesByClothesId) {
        Map<UUID, ThicknessLevel> thicknessByClothesId = new HashMap<>();
        for (UUID clothesId : clothesIds) {
            List<ThicknessLevel> levels = attributesByClothesId.getOrDefault(clothesId, List.of()).stream()
                    .map(this::analyzeThickness)
                    .toList();
            thicknessByClothesId.put(clothesId, attributeEstimator.estimateThickness(levels));
        }
        return thicknessByClothesId;
    }

    private Map<UUID, SleeveLength> estimateSleeveByClothesId(
            List<UUID> clothesIds, Map<UUID, List<ClothesAttribute>> attributesByClothesId) {
        Map<UUID, SleeveLength> sleeveByClothesId = new HashMap<>();
        for (UUID clothesId : clothesIds) {
            List<SleeveLength> lengths = attributesByClothesId.getOrDefault(clothesId, List.of()).stream()
                    .map(this::analyzeSleeve)
                    .toList();
            sleeveByClothesId.put(clothesId, attributeEstimator.estimateSleeveLength(lengths));
        }
        return sleeveByClothesId;
    }

    private ThicknessLevel analyzeThickness(ClothesAttribute attribute) {
        return attributeAnalyzer.analyze(attribute.getDefinition().getName(), attribute.getValue());
    }

    private SleeveLength analyzeSleeve(ClothesAttribute attribute) {
        return sleeveAnalyzer.analyze(attribute.getDefinition().getName(), attribute.getValue());
    }
}
