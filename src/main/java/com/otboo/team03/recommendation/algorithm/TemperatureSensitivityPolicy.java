package com.otboo.team03.recommendation.algorithm;

/** 기본 추천과 LLM 입력에서 공유하는 사용자 온도 민감도 보정 정책. */
public final class TemperatureSensitivityPolicy {

    private static final double ADJUSTMENT_PER_LEVEL = 2.0;

    private TemperatureSensitivityPolicy() { }

    public static double adjust(double temperature, int sensitivity) {
        return temperature + (sensitivity - 3) * ADJUSTMENT_PER_LEVEL;
    }
}
