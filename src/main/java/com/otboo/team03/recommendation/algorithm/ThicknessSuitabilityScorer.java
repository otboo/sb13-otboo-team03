package com.otboo.team03.recommendation.algorithm;

import org.springframework.stereotype.Component;

/** 온도 민감도가 이미 반영된 보정 온도와 두께 수준의 적합도를 평가한다. */
@Component
public class ThicknessSuitabilityScorer {

    private static final double COLD_TEMPERATURE_THRESHOLD = 15.0;
    private static final double HOT_TEMPERATURE_THRESHOLD = 25.0;
    private static final double NEUTRAL_FALLBACK_TEMPERATURE_THRESHOLD = 20.0;
    private static final int NEUTRAL_SENSITIVITY = 3;

    public int score(ThicknessLevel level, double adjustedTemperature, int sensitivity) {
        if (adjustedTemperature < COLD_TEMPERATURE_THRESHOLD) {
            return switch (level) {
                case HEAVY -> 3;
                case MEDIUM -> 2;
                case UNKNOWN -> 1;
                case LIGHT -> 0;
            };
        }

        if (adjustedTemperature >= HOT_TEMPERATURE_THRESHOLD) {
            return switch (level) {
                case LIGHT -> 3;
                case MEDIUM -> 2;
                case UNKNOWN -> 1;
                case HEAVY -> 0;
            };
        }

        // 민감도로 온도를 다시 보정하지 않고, 중간 구간의 차선만 결정한다.
        boolean preferHeavy = sensitivity < NEUTRAL_SENSITIVITY ||
                (sensitivity == NEUTRAL_SENSITIVITY && adjustedTemperature < NEUTRAL_FALLBACK_TEMPERATURE_THRESHOLD);
        return switch (level) {
            case MEDIUM -> 3;
            case HEAVY -> preferHeavy ? 2 : 0;
            case LIGHT -> preferHeavy ? 0 : 2;
            case UNKNOWN -> 1;
        };
    }
}
