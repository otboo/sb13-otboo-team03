package com.otboo.team03.global.client.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.otboo.team03.global.exception.llm.GroqRequestException;
import com.otboo.team03.global.exception.llm.InvalidLlmResponseException;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/** 코디 도메인을 알지 못하는 Groq Chat Completions 공통 통신 클라이언트. */
public class GroqClient {

    private final RestClient client;

    public GroqClient(RestClient client) {
        this.client = client;
    }

    public String complete(Map<String, Object> request) {
        Completion response;
        try {
            response = client.post().uri("/chat/completions")
                    .body(request).retrieve().body(Completion.class);
        } catch (RestClientResponseException exception) {
            throw requestFailure(exception);
        }
        if (response == null || response.choices() == null || response.choices().size() != 1) {
            throw new InvalidLlmResponseException();
        }
        Choice choice = response.choices().get(0);
        if (choice == null || !"stop".equals(choice.finishReason()) || choice.message() == null
                || choice.message().content() == null || choice.message().content().isBlank()
                || (choice.message().refusal() != null && !choice.message().refusal().isBlank())) {
            throw new InvalidLlmResponseException();
        }
        return choice.message().content();
    }

    private GroqRequestException requestFailure(RestClientResponseException exception) {
        String errorType = null;
        String errorCode = null;
        try {
            JsonNode response = exception.getResponseBodyAs(JsonNode.class);
            if (response != null) {
                JsonNode error = response.path("error");
                errorType = textValue(error.path("type"));
                errorCode = textValue(error.path("code"));
            }
        } catch (RestClientException | JacksonException | IllegalStateException ignored) {
            // 비 JSON 응답이나 해석할 수 없는 본문도 HTTP 상태를 보존해 기본 추천으로 복구한다.
        }
        return new GroqRequestException(exception.getStatusCode().value(), errorType, errorCode);
    }

    private String textValue(JsonNode node) {
        return node.isString() ? node.stringValue() : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Completion(List<Choice> choices) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content, String refusal) { }
}
