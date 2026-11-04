package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;

import java.util.List;
import java.util.Optional;

public class BasicOutfitAlgorithm {

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
