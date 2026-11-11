package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.entity.SleeveLength;
import com.otboo.team03.recommendation.entity.ThicknessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BasicOutfitAlgorithm {

    private static final double TEMPERATURE_ADJUSTMENT_PER_LEVEL = 2.0;
    private static final double OUTER_TEMPERATURE_THRESHOLD = 15.0;
    private static final List<ClothesType> ADDITIONAL_ITEM_TYPES = List.of(
            ClothesType.SHOES, ClothesType.BAG, ClothesType.HAT, ClothesType.ACCESSORY);

    private final ClothesSuitabilityScorer suitabilityScorer;

    public List<Clothes> recommend(List<Clothes> clothes,
                                  Map<UUID, ThicknessLevel> thicknessByClothesId,
                                  Map<UUID, SleeveLength> sleeveByClothesId,
                                  double temperature, int sensitivity) {
        double adjustedTemperature = adjustTemperature(temperature, sensitivity);
        Comparator<Clothes> byThicknessDescending = Comparator.comparingInt((Clothes item) ->
                suitabilityScorer.scoreThickness(thicknessOf(item, thicknessByClothesId),
                        adjustedTemperature, sensitivity)).reversed();
        Comparator<Clothes> bySleeveDescending = Comparator.comparingInt((Clothes item) ->
                suitabilityScorer.scoreSleeve(item.getType(), sleeveOf(item, sleeveByClothesId),
                        adjustedTemperature, sensitivity)).reversed();

        List<Clothes> rankedClothes = clothes.stream()
                .sorted(byThicknessDescending.thenComparing(bySleeveDescending))
                .toList();

        // 두께와 소매 점수가 모두 같으면 입력 순서를 유지한다.
        return recommendAtAdjustedTemperature(rankedClothes, clothes, adjustedTemperature);
    }

    public List<Clothes> recommend(List<Clothes> clothes,
                                  Map<UUID, ThicknessLevel> thicknessByClothesId,
                                  double temperature, int sensitivity) {
        return recommend(clothes, thicknessByClothesId, Map.of(), temperature, sensitivity);
    }

    public List<Clothes> recommend(List<Clothes> clothes, double temperature, int sensitivity) {
        return recommendAtAdjustedTemperature(clothes, clothes, adjustTemperature(temperature, sensitivity));
    }

    public List<Clothes> recommend(List<Clothes> clothes) {
        Optional<Clothes> top = firstOfType(clothes, ClothesType.TOP);
        Optional<Clothes> bottom = firstOfType(clothes, ClothesType.BOTTOM);
        Optional<Clothes> dress = firstOfType(clothes, ClothesType.DRESS);

        if (top.isPresent() && bottom.isPresent()) {
            return List.of(top.get(), bottom.get());
        }

        if (dress.isPresent()) {
            return List.of(dress.get());
        }

        return List.of();
    }

    private double adjustTemperature(double temperature, int sensitivity) {
        return temperature + (sensitivity - 3) * TEMPERATURE_ADJUSTMENT_PER_LEVEL;
    }

    private Optional<Clothes> firstOfType(List<Clothes> clothes, ClothesType type) {
        return clothes.stream().filter(item -> item.getType() == type).findFirst();
    }

    private ThicknessLevel thicknessOf(Clothes clothes, Map<UUID, ThicknessLevel> thicknessByClothesId) {
        UUID clothesId = clothes.getId();
        return clothesId == null ? ThicknessLevel.UNKNOWN : thicknessByClothesId.getOrDefault(clothesId, ThicknessLevel.UNKNOWN);
    }

    private SleeveLength sleeveOf(Clothes clothes, Map<UUID, SleeveLength> sleeveByClothesId) {
        UUID clothesId = clothes.getId();
        return clothesId == null ? SleeveLength.UNKNOWN : sleeveByClothesId.getOrDefault(clothesId, SleeveLength.UNKNOWN);
    }

    private List<Clothes> recommendAtAdjustedTemperature(
            List<Clothes> rankedClothes, List<Clothes> originalClothes, double adjustedTemperature) {
        List<Clothes> basicOutfit = recommend(rankedClothes);
        if (basicOutfit.isEmpty()) {
            return basicOutfit;
        }

        List<Clothes> outfit = new ArrayList<>(basicOutfit);
        if (adjustedTemperature < OUTER_TEMPERATURE_THRESHOLD) {
            firstOfType(rankedClothes, ClothesType.OUTER).ifPresent(outfit::add);
        }

        for (ClothesType type : ADDITIONAL_ITEM_TYPES) {
            firstOfType(originalClothes, type).ifPresent(outfit::add);
        }
        return List.copyOf(outfit);
    }
}
