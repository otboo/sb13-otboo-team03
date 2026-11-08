package com.otboo.team03.recommendation.algorithm;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.otboo.team03.recommendation.algorithm.ThicknessLevel.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 임시 온도 정책: 15도 미만은 추움, 15도 이상 25도 미만은 중간, 25도 이상은 더움. */
class ThicknessSuitabilityScorerTest {

    private final ThicknessSuitabilityScorer scorer = new ThicknessSuitabilityScorer();

    @ParameterizedTest
    @ValueSource(doubles = {-5.0, 10.0, 14.9})
    void 추운_구간에서는_HEAVY만_1점이고_나머지는_0점이다(double temperature) {
        assertThat(scorer.score(HEAVY, temperature)).isEqualTo(1);
        assertThat(scorer.score(MEDIUM, temperature)).isZero();
        assertThat(scorer.score(LIGHT, temperature)).isZero();
        assertThat(scorer.score(UNKNOWN, temperature)).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {15.0, 20.0, 24.9})
    void 중간_구간에서는_MEDIUM만_1점이고_나머지는_0점이다(double temperature) {
        assertThat(scorer.score(MEDIUM, temperature)).isEqualTo(1);
        assertThat(scorer.score(LIGHT, temperature)).isZero();
        assertThat(scorer.score(HEAVY, temperature)).isZero();
        assertThat(scorer.score(UNKNOWN, temperature)).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {25.0, 28.0, 35.0})
    void 더운_구간에서는_LIGHT만_1점이고_나머지는_0점이다(double temperature) {
        assertThat(scorer.score(LIGHT, temperature)).isEqualTo(1);
        assertThat(scorer.score(MEDIUM, temperature)).isZero();
        assertThat(scorer.score(HEAVY, temperature)).isZero();
        assertThat(scorer.score(UNKNOWN, temperature)).isZero();
    }
}
