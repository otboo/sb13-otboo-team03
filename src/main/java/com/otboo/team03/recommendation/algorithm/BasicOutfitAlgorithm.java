package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class BasicOutfitAlgorithm {

    private static final double TEMPERATURE_ADJUSTMENT_PER_LEVEL = 2.0;
    private static final double OUTER_TEMPERATURE_THRESHOLD = 15.0;

    public List<Clothes> recommend(List<Clothes> clothes, double temperature, int sensitivity) {
        List<Clothes> basicOutfit = recommend(clothes);
        double adjustedTemperature = adjustTemperature(temperature, sensitivity);

        if (basicOutfit.isEmpty() || adjustedTemperature >= OUTER_TEMPERATURE_THRESHOLD) {
            return basicOutfit;
        }

        Optional<Clothes> outer = clothes.stream()
                .filter(item -> item.getType() == ClothesType.OUTER)
                .findFirst();

        List<Clothes> outfit = new ArrayList<>(basicOutfit);
        outer.ifPresent(outfit::add);
        return List.copyOf(outfit);
    }

    private double adjustTemperature(double temperature, int sensitivity) {
        return temperature + (sensitivity - 3) * TEMPERATURE_ADJUSTMENT_PER_LEVEL;
    }

    public List<Clothes> recommend(List<Clothes> clothes) {
        Optional<Clothes> top = clothes.stream()
                .filter(item -> item.getType() == ClothesType.TOP)
                .findFirst();

        Optional<Clothes> bottom = clothes.stream()
                .filter(item -> item.getType() == ClothesType.BOTTOM)
                .findFirst();

        Optional<Clothes> dress = clothes.stream()
                .filter(item -> item.getType() == ClothesType.DRESS)
                .findFirst();

        if (top.isPresent() && bottom.isPresent()) {
            return List.of(top.get(), bottom.get());
        }

        if (dress.isPresent()) {
            return List.of(dress.get());
        }

        return List.of();
    }
}
