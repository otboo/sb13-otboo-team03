package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.recommendation.entity.ThicknessLevel;
import com.otboo.team03.recommendation.entity.SleeveLength;
import org.springframework.stereotype.Component;

import java.util.List;

/** 한 의상의 두께와 소매 길이 해석 결과를 각각 독립적으로 종합한다. */
@Component
public class ClothesAttributeEstimator {

    public ThicknessLevel estimateThickness(List<ThicknessLevel> levels) {
        ThicknessLevel result = ThicknessLevel.UNKNOWN;

        for (ThicknessLevel level : levels) {
            if (level == ThicknessLevel.UNKNOWN) {
                continue;
            }
            if (result != ThicknessLevel.UNKNOWN && result != level) {
                return ThicknessLevel.UNKNOWN;
            }
            result = level;
        }

        return result;
    }

    public SleeveLength estimateSleeveLength(List<SleeveLength> lengths) {
        return SleeveLength.UNKNOWN;
    }
}
