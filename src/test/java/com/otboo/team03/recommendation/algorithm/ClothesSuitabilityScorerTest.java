package com.otboo.team03.recommendation.algorithm;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.recommendation.entity.SleeveLength;
import com.otboo.team03.recommendation.entity.ThicknessLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static com.otboo.team03.recommendation.entity.SleeveLength.*;
import static org.assertj.core.api.Assertions.assertThat;

class ClothesSuitabilityScorerTest {

    private final ClothesSuitabilityScorer scorer = new ClothesSuitabilityScorer();

    @Nested
    @DisplayName("두께 적합도")
    class ThicknessSuitability {

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
            int firstScore = scorer.scoreThickness(first, adjustedTemperature, sensitivity);
            int secondScore = scorer.scoreThickness(second, adjustedTemperature, sensitivity);
            int thirdScore = scorer.scoreThickness(third, adjustedTemperature, sensitivity);
            int fourthScore = scorer.scoreThickness(fourth, adjustedTemperature, sensitivity);

            assertThat(firstScore).isEqualTo(3);
            assertThat(secondScore).isEqualTo(2);
            assertThat(thirdScore).isEqualTo(1);
            assertThat(fourthScore).isZero();
        }
    }

    @Nested
    @DisplayName("소매 길이 적합도")
    class SleeveSuitability {

        @ParameterizedTest
        @CsvSource({
                "TOP, -5.0, 3, LONG",
                "TOP, 14.9, 1, LONG",
                "TOP, 14.9, 5, LONG",
                "TOP, 15.0, 1, LONG",
                "TOP, 15.0, 5, SHORT",
                "TOP, 19.9, 3, LONG",
                "TOP, 20.0, 3, SHORT",
                "TOP, 20.0, 2, LONG",
                "TOP, 20.0, 4, SHORT",
                "TOP, 24.9, 1, LONG",
                "TOP, 25.0, 1, SHORT",
                "TOP, 25.0, 5, SHORT",
                "TOP, 35.0, 3, SHORT",
                "DRESS, 10.0, 5, LONG",
                "DRESS, 28.0, 1, SHORT",
                "DRESS, 17.0, 1, LONG",
                "DRESS, 23.0, 5, SHORT",
                "DRESS, 19.9, 3, LONG",
                "DRESS, 20.0, 3, SHORT"
        })
        void 상의와_원피스는_선호_소매_2점_UNKNOWN_1점_반대_소매_0점이다(
                ClothesType type, double adjustedTemperature, int sensitivity, SleeveLength preferred) {
            SleeveLength opposite = preferred == SHORT ? LONG : SHORT;

            assertThat(scorer.scoreSleeve(type, preferred, adjustedTemperature, sensitivity)).isEqualTo(2);
            assertThat(scorer.scoreSleeve(type, UNKNOWN, adjustedTemperature, sensitivity)).isEqualTo(1);
            assertThat(scorer.scoreSleeve(type, opposite, adjustedTemperature, sensitivity)).isZero();
        }

        @ParameterizedTest
        @EnumSource(value = ClothesType.class, names = {"TOP", "DRESS"}, mode = EnumSource.Mode.EXCLUDE)
        void 적용_대상이_아닌_종류는_온도와_소매에_관계없이_0점이다(ClothesType type) {
            for (SleeveLength length : SleeveLength.values()) {
                for (double temperature : new double[]{10.0, 20.0, 28.0}) {
                    for (int sensitivity : new int[]{1, 3, 5}) {
                        assertThat(scorer.scoreSleeve(type, length, temperature, sensitivity))
                                .as("종류=%s, 소매=%s, 보정 온도=%s, 민감도=%s",
                                        type, length, temperature, sensitivity)
                                .isZero();
                    }
                }
            }
        }
    }
}
