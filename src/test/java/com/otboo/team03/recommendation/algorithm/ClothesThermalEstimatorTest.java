package com.otboo.team03.recommendation.algorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static com.otboo.team03.recommendation.algorithm.ThicknessLevel.*;
import static org.assertj.core.api.Assertions.assertThat;

class ClothesThermalEstimatorTest {

    private final ClothesThermalEstimator estimator = new ClothesThermalEstimator();

    @Test
    void 속성이_없으면_UNKNOWN이다() {
        assertThat(estimator.estimate(List.of())).isEqualTo(UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(value = ThicknessLevel.class, names = {"LIGHT", "MEDIUM", "HEAVY"})
    void 유효한_두께가_하나면_그_수준을_사용한다(ThicknessLevel level) {
        assertThat(estimator.estimate(List.of(level))).isEqualTo(level);
    }

    @ParameterizedTest
    @EnumSource(value = ThicknessLevel.class, names = {"LIGHT", "MEDIUM", "HEAVY"})
    void 같은_두께가_중복되어도_수준이_달라지지_않는다(ThicknessLevel level) {
        assertThat(estimator.estimate(List.of(level, level))).isEqualTo(level);
    }

    @Test
    void 해석_불가한_속성만_있으면_UNKNOWN이다() {
        assertThat(estimator.estimate(List.of(UNKNOWN, UNKNOWN))).isEqualTo(UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(value = ThicknessLevel.class, names = {"LIGHT", "MEDIUM", "HEAVY"})
    void 해석_불가한_속성은_유효한_두께를_덮어쓰지_않는다(ThicknessLevel level) {
        assertThat(estimator.estimate(List.of(UNKNOWN, level, UNKNOWN)))
                .isEqualTo(level);
    }

    @Test
    void 서로_다른_두께가_있으면_UNKNOWN이다() {
        assertThat(estimator.estimate(List.of(LIGHT, HEAVY))).isEqualTo(UNKNOWN);
        assertThat(estimator.estimate(List.of(LIGHT, MEDIUM))).isEqualTo(UNKNOWN);
        assertThat(estimator.estimate(List.of(MEDIUM, HEAVY))).isEqualTo(UNKNOWN);
    }

    @Test
    void 두께_충돌은_입력_순서와_관계없이_UNKNOWN이다() {
        assertThat(estimator.estimate(List.of(HEAVY, LIGHT))).isEqualTo(UNKNOWN);
    }

    @Test
    void 같은_두께가_더_많아도_다른_두께와_충돌하면_UNKNOWN이다() {
        assertThat(estimator.estimate(List.of(HEAVY, HEAVY, LIGHT, UNKNOWN)))
                .isEqualTo(UNKNOWN);
    }
}
