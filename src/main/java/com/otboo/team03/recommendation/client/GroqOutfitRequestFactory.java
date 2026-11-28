package com.otboo.team03.recommendation.client;

import com.otboo.team03.recommendation.config.RecommendationLlmProperties;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 모델 지시문과 소유 의상 ID 응답 스키마를 구성한다. HTTP 전송은 클라이언트가 담당한다. */
@Component
public class GroqOutfitRequestFactory {

    private final RecommendationLlmProperties properties;
    private final String systemPrompt;
    private final String referencePrompt;

    public GroqOutfitRequestFactory(RecommendationLlmProperties properties,
            @Value("classpath:prompts/outfit-recommendation-system.txt") Resource promptResource) throws IOException {
        this.properties = properties;
        this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
        this.referencePrompt = new ClassPathResource("prompts/outfit-reference-guidance.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public Map<String, Object> create(LlmRecommendationInput input, String serializedInput, String serializedReferences) {
        boolean hasReferences = serializedReferences != null && !serializedReferences.isBlank();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt + (hasReferences ? "\n" + referencePrompt : "")));
        messages.add(Map.of("role", "user", "content", serializedInput));
        if (hasReferences) messages.add(Map.of("role", "user", "content", serializedReferences));
        return Map.of(
                "model", properties.model(),
                "messages", messages,
                "reasoning_effort", "low",
                "max_completion_tokens", properties.maxCompletionTokens(),
                "response_format", Map.of("type", "json_schema", "json_schema",
                        Map.of("name", "outfit_recommendation", "strict", true, "schema", schema(input))));
    }

    private Map<String, Object> schema(LlmRecommendationInput input) {
        Map<String, Object> clothesId = Map.of("type", "string", "enum", input.wardrobe().stream()
                .map(item -> item.clothesId().toString()).toList());
        return object(Map.of("clothesIds", Map.of("type", "array", "items", clothesId)), List.of("clothesIds"));
    }

    private Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties,
                "required", required, "additionalProperties", false);
    }
}
