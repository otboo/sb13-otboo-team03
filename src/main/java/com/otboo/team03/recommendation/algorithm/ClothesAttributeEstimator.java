package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.recommendation.entity.ThicknessLevel;
import com.otboo.team03.recommendation.entity.SleeveLength;
import org.springframework.stereotype.Component;

import java.util.List;

/** 한 의상의 두께와 소매 길이 해석 결과를 각각 독립적으로 종합한다. */
@Component
public class ClothesAttributeEstimator {

    public ThicknessLevel estimateThickness(List<ThicknessLevel> levels) {
        return estimate(levels, ThicknessLevel.UNKNOWN);
    }

    public SleeveLength estimateSleeveLength(List<SleeveLength> lengths) {
        return estimate(lengths, SleeveLength.UNKNOWN);
    }

    /** UNKNOWN을 제외한 유효 값이 하나로 일치할 때만 해당 값을 반환한다. */
    private <T extends Enum<T>> T estimate(List<T> values, T unknown) {
        T result = unknown;

        for (T value : values) {
            if (value == unknown) {
                continue;
            }
            if (result != unknown && result != value) {
                return unknown;
            }
            result = value;
        }

        return result;
    }
}
