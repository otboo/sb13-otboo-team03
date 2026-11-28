package com.otboo.team03.recommendation.client;

import com.otboo.team03.global.client.groq.GroqClient;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import com.otboo.team03.recommendation.rag.StyleReference;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 코디 입력과 결과 DTO를 공통 Groq 통신에 연결한다. */
@Component
@Slf4j
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
        return recommend(input, serializedInput, null);
    }

    public String serializeReferences(List<StyleReference.Context> references) {
        return objectMapper.writeValueAsString(Map.of("styleReferences", references));
    }

    public LlmOutfitDecision recommend(LlmRecommendationInput input, String serializedInput, String serializedReferences) {
        var request = requestFactory.create(input, serializedInput, serializedReferences);
        int messageCount = ((List<?>) request.get("messages")).size();
        log.debug("LLM 의상 추천 전송: messages={}, ragContext={}", messageCount, messageCount == 3);
        String content = client.complete(request);
        log.debug("LLM 의상 추천 응답 수신: ragContext={}", messageCount == 3);
        return objectMapper.readValue(content, LlmOutfitDecision.class);
    }
}
