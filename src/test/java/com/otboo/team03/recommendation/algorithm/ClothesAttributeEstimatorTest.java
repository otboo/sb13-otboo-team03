package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.recommendation.entity.ThicknessLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import com.otboo.team03.recommendation.entity.SleeveLength;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static com.otboo.team03.recommendation.entity.ThicknessLevel.*;
import static org.assertj.core.api.Assertions.assertThat;

class ClothesAttributeEstimatorTest {

    private final ClothesAttributeEstimator estimator = new ClothesAttributeEstimator();

    @Nested
    @DisplayName("두께 정보 종합")
    class ThicknessEstimation {
        @Test
        void 속성이_없으면_UNKNOWN이다() {
            assertThat(estimator.estimateThickness(List.of())).isEqualTo(UNKNOWN);
        }

        @ParameterizedTest
        @EnumSource(value = ThicknessLevel.class, names = {"LIGHT", "MEDIUM", "HEAVY"})
        void 유효한_두께가_하나면_그_수준을_사용한다(ThicknessLevel level) {
            assertThat(estimator.estimateThickness(List.of(level))).isEqualTo(level);
        }

        @ParameterizedTest
        @EnumSource(value = ThicknessLevel.class, names = {"LIGHT", "MEDIUM", "HEAVY"})
        void 같은_두께가_중복되어도_수준이_달라지지_않는다(ThicknessLevel level) {
            assertThat(estimator.estimateThickness(List.of(level, level))).isEqualTo(level);
        }

        @Test
        void 해석_불가한_속성만_있으면_UNKNOWN이다() {
            assertThat(estimator.estimateThickness(List.of(UNKNOWN, UNKNOWN))).isEqualTo(UNKNOWN);
        }

        @ParameterizedTest
        @EnumSource(value = ThicknessLevel.class, names = {"LIGHT", "MEDIUM", "HEAVY"})
        void 해석_불가한_속성은_유효한_두께를_덮어쓰지_않는다(ThicknessLevel level) {
            assertThat(estimator.estimateThickness(List.of(UNKNOWN, level, UNKNOWN)))
                    .isEqualTo(level);
        }

        @Test
        void 서로_다른_두께가_있으면_UNKNOWN이다() {
            assertThat(estimator.estimateThickness(List.of(LIGHT, HEAVY))).isEqualTo(UNKNOWN);
            assertThat(estimator.estimateThickness(List.of(LIGHT, MEDIUM))).isEqualTo(UNKNOWN);
            assertThat(estimator.estimateThickness(List.of(MEDIUM, HEAVY))).isEqualTo(UNKNOWN);
        }

        @Test
        void 두께_충돌은_입력_순서와_관계없이_UNKNOWN이다() {
            assertThat(estimator.estimateThickness(List.of(HEAVY, LIGHT))).isEqualTo(UNKNOWN);
        }

        @Test
        void 같은_두께가_더_많아도_다른_두께와_충돌하면_UNKNOWN이다() {
            assertThat(estimator.estimateThickness(List.of(HEAVY, HEAVY, LIGHT, UNKNOWN)))
                    .isEqualTo(UNKNOWN);
        }
    }

    @Nested
    @DisplayName("소매 길이 정보 종합")
    class SleeveLengthEstimation {
        @Test
        void 소매_정보가_없으면_UNKNOWN이다() {
            assertThat(estimator.estimateSleeveLength(List.of())).isEqualTo(SleeveLength.UNKNOWN);
        }

        @ParameterizedTest
        @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
        void 유효한_소매가_하나면_그_길이를_사용한다(SleeveLength length) {
            assertThat(estimator.estimateSleeveLength(List.of(length))).isEqualTo(length);
        }

        @ParameterizedTest
        @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
        void 같은_소매가_중복되어도_길이가_달라지지_않는다(SleeveLength length) {
            assertThat(estimator.estimateSleeveLength(List.of(length, length))).isEqualTo(length);
        }

        @Test
        void 해석_불가한_정보만_있으면_UNKNOWN이다() {
            assertThat(estimator.estimateSleeveLength(List.of(SleeveLength.UNKNOWN, SleeveLength.UNKNOWN))).isEqualTo(SleeveLength.UNKNOWN);
        }

        @ParameterizedTest
        @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
        void UNKNOWN은_유효한_소매_길이를_덮어쓰지_않는다(SleeveLength length) {
            assertThat(estimator.estimateSleeveLength(List.of(SleeveLength.UNKNOWN, length, SleeveLength.UNKNOWN)))
                    .isEqualTo(length);
        }

        @ParameterizedTest
        @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
        void 반팔과_긴팔이_함께_있으면_입력_순서와_관계없이_UNKNOWN이다(SleeveLength first) {
            SleeveLength other = first == SleeveLength.SHORT ? SleeveLength.LONG : SleeveLength.SHORT;

            assertThat(estimator.estimateSleeveLength(List.of(first, other))).isEqualTo(SleeveLength.UNKNOWN);
        }

        @ParameterizedTest
        @EnumSource(value = SleeveLength.class, names = {"SHORT", "LONG"})
        void 한쪽_소매가_더_많아도_다른_길이와_충돌하면_UNKNOWN이다(SleeveLength majority) {
            SleeveLength other = majority == SleeveLength.SHORT ? SleeveLength.LONG : SleeveLength.SHORT;

            assertThat(estimator.estimateSleeveLength(List.of(majority, majority, other, SleeveLength.UNKNOWN)))
                    .isEqualTo(SleeveLength.UNKNOWN);
        }
    }
}
