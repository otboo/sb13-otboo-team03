package com.otboo.team03.recommendation.algorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SleeveLengthAnalyzerTest {

    private final SleeveLengthAnalyzer analyzer = new SleeveLengthAnalyzer();

    @ParameterizedTest
    @CsvSource({
            "소매, 반팔, SHORT",
            "소매, 긴팔, LONG",
            "소매 길이, 반팔, SHORT",
            "소매 길이, 긴팔, LONG"
    })
    void 사전에_정의된_소매_속성과_선택값을_해석한다(
            String name, String value, SleeveLength expected) {
        assertThat(analyzer.analyze(name, value)).isEqualTo(expected);
    }

    @Test
    void 속성명과_선택값의_앞뒤_공백을_제거한다() {
        assertThat(analyzer.analyze(" \t소매 길이\n", " 반팔 ")).isEqualTo(SleeveLength.SHORT);
        assertThat(analyzer.analyze(" 소매 ", "\t긴팔\n")).isEqualTo(SleeveLength.LONG);
    }

    @ParameterizedTest
    @CsvSource({
            "색상, 반팔",
            "두께, 긴팔",
            "소매, 민소매",
            "소매, 7부",
            "소매, 9부",
            "소매, 반팔 아님",
            "소매추가, 긴팔",
            "소 매, 긴팔",
            "소매 길이, 긴 팔"
    })
    void 미지원_표현은_부분일치로_추측하지_않는다(String name, String value) {
        assertThat(analyzer.analyze(name, value)).isEqualTo(SleeveLength.UNKNOWN);
    }

    @ParameterizedTest
    @MethodSource("missingInputs")
    void 누락되거나_빈_입력은_UNKNOWN이다(String name, String value) {
        assertThat(analyzer.analyze(name, value)).isEqualTo(SleeveLength.UNKNOWN);
    }

    static Stream<Arguments> missingInputs() {
        return Stream.of(
                Arguments.of(null, "반팔"),
                Arguments.of("소매", null),
                Arguments.of(null, null),
                Arguments.of("", "반팔"),
                Arguments.of("소매", ""),
                Arguments.of(" \t", "긴팔"),
                Arguments.of("소매 길이", " \n")
        );
    }
}
