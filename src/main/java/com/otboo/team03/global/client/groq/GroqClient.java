package com.otboo.team03.global.client.groq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.otboo.team03.global.exception.InvalidLlmResponseException;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

/** 코디 도메인을 알지 못하는 Groq Chat Completions 공통 통신 클라이언트. */
public class GroqClient {

    private final RestClient client;

    public GroqClient(RestClient client) {
        this.client = client;
    }

    public String complete(Map<String, Object> request) {
        Completion response = client.post().uri("/chat/completions")
                .body(request).retrieve().body(Completion.class);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Completion(List<Choice> choices) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content, String refusal) { }
}
