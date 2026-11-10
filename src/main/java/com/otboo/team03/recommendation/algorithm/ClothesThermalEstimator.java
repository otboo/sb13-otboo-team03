package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.recommendation.entity.ThicknessLevel;
import org.springframework.stereotype.Component;

import java.util.List;

/** 한 의상의 속성별 두께 해석 결과를 종합한다. */
@Component
public class ClothesThermalEstimator {

    public ThicknessLevel estimate(List<ThicknessLevel> levels) {
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
}
