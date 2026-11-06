package com.otboo.team03.recommendation.algorithm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AttributeSemanticAnalyzerTest {

    private final AttributeSemanticAnalyzer analyzer = new AttributeSemanticAnalyzer();

    @Test
    void 두께가_얇음이면_LIGHT로_해석한다() {
        assertThat(analyzer.analyze("두께", "얇음"))
                .isEqualTo(ThicknessLevel.LIGHT);
    }

    @ParameterizedTest
    @CsvSource({
            "두께, 보통, MEDIUM",
            "두께, 두꺼움, HEAVY",
            "두께, 도톰함, HEAVY",
            "두께감, 얇음, LIGHT",
            "두께감, 보통, MEDIUM",
            "두께감, 두꺼움, HEAVY",
            "두께감, 도톰함, HEAVY"
    })
    void 사전에_정의된_속성명과_선택값을_해석한다(
            String name, String value, ThicknessLevel expected) {
        assertThat(analyzer.analyze(name, value)).isEqualTo(expected);
    }

    @Test
    void 속성명과_선택값의_앞뒤_공백을_제거한다() {
        assertThat(analyzer.analyze(" \t두께감\n", " 도톰함 "))
                .isEqualTo(ThicknessLevel.HEAVY);
    }

    @ParameterizedTest
    @CsvSource({
            "색상, 검정",
            "가격대, 두꺼움",
            "두께, 알수없음",
            "두께, 두꺼움 아님",
            "두께, 기모 없음",
            "두께추가, 얇음",
            "두 께, 얇음"
    })
    void 사전에_없는_표현은_부분일치로_추측하지_않는다(String name, String value) {
        assertThat(analyzer.analyze(name, value)).isEqualTo(ThicknessLevel.UNKNOWN);
    }

    @ParameterizedTest
    @MethodSource("missingInputs")
    void 누락되거나_빈_입력은_UNKNOWN이다(String name, String value) {
        assertThat(analyzer.analyze(name, value)).isEqualTo(ThicknessLevel.UNKNOWN);
    }

    static Stream<Arguments> missingInputs() {
        return Stream.of(
                Arguments.of(null, "얇음"),
                Arguments.of("두께", null),
                Arguments.of(null, null),
                Arguments.of("", "얇음"),
                Arguments.of("두께", ""),
                Arguments.of(" \t", "얇음"),
                Arguments.of("두께", " \n")
        );
    }
}
