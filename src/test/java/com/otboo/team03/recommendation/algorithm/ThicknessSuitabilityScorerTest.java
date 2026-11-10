package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.recommendation.entity.ThicknessLevel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 추천 정책 문서의 온도·민감도별 두께 순위와 정확한 점수를 검증한다. */
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
    void 보정_온도와_민감도에_따른_두께_순위별_점수는_3_2_1_0이다(
            double adjustedTemperature, int sensitivity,
            ThicknessLevel first, ThicknessLevel second,
            ThicknessLevel third, ThicknessLevel fourth) {
        int firstScore = scorer.score(first, adjustedTemperature, sensitivity);
        int secondScore = scorer.score(second, adjustedTemperature, sensitivity);
        int thirdScore = scorer.score(third, adjustedTemperature, sensitivity);
        int fourthScore = scorer.score(fourth, adjustedTemperature, sensitivity);

        assertThat(firstScore).isEqualTo(3);
        assertThat(secondScore).isEqualTo(2);
        assertThat(thirdScore).isEqualTo(1);
        assertThat(fourthScore).isZero();
    }
}
