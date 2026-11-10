package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.recommendation.entity.SleeveLength;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static com.otboo.team03.recommendation.entity.SleeveLength.*;
import static org.assertj.core.api.Assertions.assertThat;

class ClothesSleeveEstimatorTest {

    private final ClothesSleeveEstimator estimator = new ClothesSleeveEstimator();

    @Test
    void 소매_정보가_없으면_UNKNOWN이다() {
        assertThat(estimator.estimate(List.of())).isEqualTo(UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
    void 유효한_소매가_하나면_그_길이를_사용한다(SleeveLength length) {
        assertThat(estimator.estimate(List.of(length))).isEqualTo(length);
    }

    @ParameterizedTest
    @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
    void 같은_소매가_중복되어도_길이가_달라지지_않는다(SleeveLength length) {
        assertThat(estimator.estimate(List.of(length, length))).isEqualTo(length);
    }

    @Test
    void 해석_불가한_정보만_있으면_UNKNOWN이다() {
        assertThat(estimator.estimate(List.of(UNKNOWN, UNKNOWN))).isEqualTo(UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
    void UNKNOWN은_유효한_소매_길이를_덮어쓰지_않는다(SleeveLength length) {
        assertThat(estimator.estimate(List.of(UNKNOWN, length, UNKNOWN)))
                .isEqualTo(length);
    }

    @ParameterizedTest
    @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
    void 반팔과_긴팔이_함께_있으면_입력_순서와_관계없이_UNKNOWN이다(SleeveLength first) {
        SleeveLength other = first == SHORT ? LONG : SHORT;

        assertThat(estimator.estimate(List.of(first, other))).isEqualTo(UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
    void 한쪽_소매가_더_많아도_다른_길이와_충돌하면_UNKNOWN이다(SleeveLength majority) {
        SleeveLength other = majority == SHORT ? LONG : SHORT;

        assertThat(estimator.estimate(List.of(majority, majority, other, UNKNOWN)))
                .isEqualTo(UNKNOWN);
    }
}
