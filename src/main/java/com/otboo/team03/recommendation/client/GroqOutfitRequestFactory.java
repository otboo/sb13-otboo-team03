package com.otboo.team03.recommendation.client;

import com.otboo.team03.recommendation.config.RecommendationLlmProperties;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** 모델 지시문과 자유 속성 응답 스키마를 구성한다. HTTP 전송은 클라이언트가 담당한다. */
@Component
public class GroqOutfitRequestFactory {

    private final RecommendationLlmProperties properties;
    private final String systemPrompt;

    public GroqOutfitRequestFactory(RecommendationLlmProperties properties,
            @Value("classpath:prompts/outfit-recommendation-system.txt") Resource promptResource) throws IOException {
        this.properties = properties;
        this.systemPrompt = promptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    public Map<String, Object> create(LlmRecommendationInput input, String serializedInput) {
        return Map.of(
                "model", properties.model(),
                "messages", List.of(Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", serializedInput)),
                "reasoning_effort", "low",
                "max_completion_tokens", properties.maxCompletionTokens(),
                "response_format", Map.of("type", "json_schema", "json_schema",
                        Map.of("name", "outfit_recommendation", "strict", true, "schema", schema(input))));
    }

    private Map<String, Object> schema(LlmRecommendationInput input) {
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> evidence = Map.of(
                "type", "string",
                "description", "선택한 해당 의상의 원문에서 그대로 복사한 연속된 문구. "
                        + "source가 TITLE이면 의상의 name, ATTRIBUTE이면 attributes[].value 중 하나에서 인용한다. "
                        + "원문에 없는 속성 이름이나 구분 기호, JSON 구문을 덧붙이지 않는다. "
                        + "예: 속성이 {name: 두께, value: 얇음}이면 evidence는 얇음이며 두께: 얇음이 아니다.");
        Map<String, Object> clothesId = Map.of("type", "string", "enum", input.wardrobe().stream()
                .map(item -> item.clothesId().toString()).toList());
        Map<String, Object> feature = object(Map.of("name", text, "value", text, "evidence", evidence,
                "source", Map.of("type", "string", "enum", Arrays.stream(LlmOutfitDecision.Source.values()).map(Enum::name).toList())),
                List.of("name", "value", "evidence", "source"));
        Map<String, Object> analysis = object(Map.of("clothesId", clothesId,
                "features", Map.of("type", "array", "items", feature)), List.of("clothesId", "features"));
        return object(Map.of("clothesIds", Map.of("type", "array", "items", clothesId),
                        "analyses", Map.of("type", "array", "items", analysis), "reason", text),
                List.of("clothesIds", "analyses", "reason"));
    }

    private Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties,
                "required", required, "additionalProperties", false);
    }
}
