package com.otboo.team03.recommendation.client;

import com.otboo.team03.global.client.groq.GroqClient;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 코디 입력과 결과 DTO를 공통 Groq 통신에 연결한다. */
@Component
public class GroqOutfitClient {

    private final GroqClient client;
    private final ObjectMapper objectMapper;
    private final GroqOutfitRequestFactory requestFactory;

    public GroqOutfitClient(@Qualifier("groqClient") GroqClient client,
                           ObjectMapper objectMapper, GroqOutfitRequestFactory requestFactory) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.requestFactory = requestFactory;
    }

    public String serializeInput(LlmRecommendationInput input) {
        return objectMapper.writeValueAsString(input);
    }

    public LlmOutfitDecision recommend(LlmRecommendationInput input, String serializedInput) {
        String content = client.complete(requestFactory.create(input, serializedInput));
        return objectMapper.readValue(content, LlmOutfitDecision.class);
    }
}
