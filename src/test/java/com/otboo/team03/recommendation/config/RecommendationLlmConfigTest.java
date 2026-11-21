package com.otboo.team03.recommendation.config;

import com.otboo.team03.global.config.GroqProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RecommendationLlmConfigTest {
    @Test
    void 활성화할때_공통_Groq_API키가_없으면_설정_오류를_알린다() {
        assertThatThrownBy(() -> new RecommendationLlmConfig(properties(true), groqProperties("")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비활성화상태에서는_API키가_없어도_실행된다() {
        assertThatCode(() -> new RecommendationLlmConfig(properties(false), groqProperties("")))
                .doesNotThrowAnyException();
    }

    private RecommendationLlmProperties properties(boolean enabled) {
        return new RecommendationLlmProperties(enabled, "model", 10, 1000, 100);
    }

    private GroqProperties groqProperties(String apiKey) {
        return new GroqProperties(apiKey, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }
}
