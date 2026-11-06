package com.otboo.team03.recommendation.algorithm;

/** 온도 민감도가 이미 반영된 보정 온도와 두께 수준의 적합도를 평가한다. */
public class ThicknessSuitabilityScorer {

    public int score(ThicknessLevel level, double adjustedTemperature) {
        ThicknessLevel preferredLevel;
        if (adjustedTemperature < 15.0) {
            preferredLevel = ThicknessLevel.HEAVY;
        } else if (adjustedTemperature < 25.0) {
            preferredLevel = ThicknessLevel.MEDIUM;
        } else {
            preferredLevel = ThicknessLevel.LIGHT;
        }

        return level == preferredLevel ? 1 : 0;
    }
}
