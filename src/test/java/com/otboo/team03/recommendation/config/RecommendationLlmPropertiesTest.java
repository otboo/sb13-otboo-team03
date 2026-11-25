package com.otboo.team03.recommendation.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class RecommendationLlmPropertiesTest {
    @ParameterizedTest
    @CsvSource({"0, 1, 1", "-1, 1, 1", "1, 0, 1", "1, -1, 1", "1, 1, 0", "1, 1, -1"})
    void 입력과_출력_제한은_양수여야_한다(int maxClothes, int maxInputCharacters, int maxCompletionTokens) {
        assertThatThrownBy(() -> new RecommendationLlmProperties(
                true, "model", maxClothes, maxInputCharacters, maxCompletionTokens))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LLM 추천 입력·출력 제한은 양수여야 합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void 활성화하면_모델이_필요하다(String model) {
        assertThatThrownBy(() -> new RecommendationLlmProperties(true, model, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("LLM 추천 활성화 시 모델이 필요합니다.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 비활성화하면_모델이_없어도_된다(String model) {
        assertThatCode(() -> new RecommendationLlmProperties(false, model, 1, 1, 1))
                .doesNotThrowAnyException();
    }

    @Test
    void 활성화하면_유효한_모델과_최소_양수_제한을_허용한다() {
        assertThatCode(() -> new RecommendationLlmProperties(true, "model", 1, 1, 1))
                .doesNotThrowAnyException();
    }
}
