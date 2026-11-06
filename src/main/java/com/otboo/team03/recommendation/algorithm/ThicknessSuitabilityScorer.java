package com.otboo.team03.recommendation.algorithm;

/** 온도 민감도가 이미 반영된 보정 온도와 두께 수준의 적합도를 평가한다. */
public class ThicknessSuitabilityScorer {

    private static final double COLD_TEMPERATURE_THRESHOLD = 15.0;
    private static final double HOT_TEMPERATURE_THRESHOLD = 25.0;

    public int score(ThicknessLevel level, double adjustedTemperature) {
        ThicknessLevel preferredLevel;
        if (adjustedTemperature < COLD_TEMPERATURE_THRESHOLD) {
            preferredLevel = ThicknessLevel.HEAVY;
        } else if (adjustedTemperature < HOT_TEMPERATURE_THRESHOLD) {
            preferredLevel = ThicknessLevel.MEDIUM;
        } else {
            preferredLevel = ThicknessLevel.LIGHT;
        }

        return level == preferredLevel ? 1 : 0;
    }
}
