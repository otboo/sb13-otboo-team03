package com.otboo.team03.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 추천 기능 전용 정책. API 키와 HTTP 시간 제한은 global의 GroqProperties가 관리한다. */
@ConfigurationProperties(prefix = "otboo.recommendation.llm")
public record RecommendationLlmProperties(boolean enabled, String model, int maxClothes,
                                          int maxInputCharacters, int maxCompletionTokens) {
    public RecommendationLlmProperties {
        if (maxClothes < 1 || maxInputCharacters < 1 || maxCompletionTokens < 1) {
            throw new IllegalArgumentException("LLM 추천 입력·출력 제한은 양수여야 합니다.");
        }
        if (enabled && (model == null || model.isBlank())) {
            throw new IllegalArgumentException("LLM 추천 활성화 시 모델이 필요합니다.");
        }
    }
}
