package com.otboo.team03.recommendation.algorithm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 추천 정책 문서의 온도·민감도별 전체 두께 순위를 검증한다. */
class ThicknessSuitabilityScorerTest {

    private final ThicknessSuitabilityScorer scorer = new ThicknessSuitabilityScorer();

    @ParameterizedTest
    @CsvSource({
            "-5.0, 3, HEAVY, MEDIUM, UNKNOWN, LIGHT",
            "14.9, 1, HEAVY, MEDIUM, UNKNOWN, LIGHT",
            "14.9, 5, HEAVY, MEDIUM, UNKNOWN, LIGHT",
            "15.0, 1, MEDIUM, HEAVY, UNKNOWN, LIGHT",
            "15.0, 3, MEDIUM, HEAVY, UNKNOWN, LIGHT",
            "15.0, 5, MEDIUM, LIGHT, UNKNOWN, HEAVY",
            "17.0, 1, MEDIUM, HEAVY, UNKNOWN, LIGHT",
            "20.0, 2, MEDIUM, HEAVY, UNKNOWN, LIGHT",
            "24.9, 1, MEDIUM, HEAVY, UNKNOWN, LIGHT",
            "19.9, 3, MEDIUM, HEAVY, UNKNOWN, LIGHT",
            "20.0, 3, MEDIUM, LIGHT, UNKNOWN, HEAVY",
            "24.9, 3, MEDIUM, LIGHT, UNKNOWN, HEAVY",
            "15.1, 5, MEDIUM, LIGHT, UNKNOWN, HEAVY",
            "20.0, 4, MEDIUM, LIGHT, UNKNOWN, HEAVY",
            "23.0, 5, MEDIUM, LIGHT, UNKNOWN, HEAVY",
            "25.0, 1, LIGHT, MEDIUM, UNKNOWN, HEAVY",
            "25.0, 3, LIGHT, MEDIUM, UNKNOWN, HEAVY",
            "25.0, 5, LIGHT, MEDIUM, UNKNOWN, HEAVY",
            "35.0, 3, LIGHT, MEDIUM, UNKNOWN, HEAVY"
    })
    void 보정_온도와_민감도에_따른_두께_우선순위를_보장한다(
            double adjustedTemperature, int sensitivity,
            ThicknessLevel first, ThicknessLevel second,
            ThicknessLevel third, ThicknessLevel fourth) {
        int firstScore = scorer.score(first, adjustedTemperature, sensitivity);
        int secondScore = scorer.score(second, adjustedTemperature, sensitivity);
        int thirdScore = scorer.score(third, adjustedTemperature, sensitivity);
        int fourthScore = scorer.score(fourth, adjustedTemperature, sensitivity);

        assertThat(firstScore).isGreaterThan(secondScore);
        assertThat(secondScore).isGreaterThan(thirdScore);
        assertThat(thirdScore).isGreaterThan(fourthScore);
    }
}
