package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.entity.SleeveLength;
import com.otboo.team03.recommendation.entity.ThicknessLevel;
import org.springframework.stereotype.Component;

/** 보정 온도를 사용하여 두께와 소매 길이의 적합도를 각각 평가한다. */
@Component
public class ClothesSuitabilityScorer {

    private static final double COLD_TEMPERATURE_THRESHOLD = 15.0;
    private static final double HOT_TEMPERATURE_THRESHOLD = 25.0;
    private static final double NEUTRAL_FALLBACK_TEMPERATURE_THRESHOLD = 20.0;
    private static final int NEUTRAL_SENSITIVITY = 3;

    public int scoreThickness(ThicknessLevel level, double adjustedTemperature, int sensitivity) {
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

        boolean preferHeavy = prefersWarmth(adjustedTemperature, sensitivity);
        return switch (level) {
            case MEDIUM -> 3;
            case HEAVY -> preferHeavy ? 2 : 0;
            case LIGHT -> preferHeavy ? 0 : 2;
            case UNKNOWN -> 1;
        };
    }

    public int scoreSleeve(ClothesType type, SleeveLength length, double adjustedTemperature, int sensitivity) {
        if (type != ClothesType.TOP && type != ClothesType.DRESS) {
            return 0;
        }
        if (length == SleeveLength.UNKNOWN) {
            return 1;
        }

        SleeveLength preferredLength;
        if (adjustedTemperature < COLD_TEMPERATURE_THRESHOLD) {
            preferredLength = SleeveLength.LONG;
        } else if (adjustedTemperature >= HOT_TEMPERATURE_THRESHOLD) {
            preferredLength = SleeveLength.SHORT;
        } else {
            preferredLength = prefersWarmth(adjustedTemperature, sensitivity) ? SleeveLength.LONG : SleeveLength.SHORT;
        }

        return length == preferredLength ? 2 : 0;
    }

    /** 중간 온도 구간에서 온도를 다시 보정하지 않고 두께 차선과 소매 선호를 결정한다. */
    private boolean prefersWarmth(double adjustedTemperature, int sensitivity) {
        return sensitivity < NEUTRAL_SENSITIVITY ||
                (sensitivity == NEUTRAL_SENSITIVITY && adjustedTemperature < NEUTRAL_FALLBACK_TEMPERATURE_THRESHOLD);
    }
}
