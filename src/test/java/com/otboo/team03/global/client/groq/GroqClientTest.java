package com.otboo.team03.global.client.groq;

import com.otboo.team03.global.exception.InvalidLlmResponseException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GroqClientTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private GroqClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.groq.com/openai/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GroqClient(builder.build());
    }

    @Test
    void 추천_DTO_없이_일반_요청을_보내고_완료된_응답_본문을_반환한다() {
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(completion("답변", "stop", null), MediaType.APPLICATION_JSON));
        assertThat(client.complete(request())).isEqualTo("답변");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"length", "content_filter", "tool_calls"})
    void 완료되지_않은_응답은_추천으로_사용하지_않는다(String finishReason) {
        server.expect(anything()).andRespond(withSuccess(
                completion("{}", finishReason, null), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOf(InvalidLlmResponseException.class);
        server.verify();
    }

    @Test
    void 거부_응답은_추천으로_사용하지_않는다() {
        server.expect(anything()).andRespond(withSuccess(completion("{}", "stop", "refused"), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOf(InvalidLlmResponseException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"choices\":[]}", "{\"choices\":[null]}"})
    void 응답_필수_값이_없으면_기본_추천으로_복구할_예외를_던진다(String response) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOf(InvalidLlmResponseException.class);
    }

    private Map<String, Object> request() {
        return Map.of("model", "test-model", "messages", List.of(Map.of("role", "user", "content", "질문")));
    }

    private String completion(String content, String finishReason, String refusal) {
        return mapper.writeValueAsString(new GroqClient.Completion(List.of(
                new GroqClient.Choice(new GroqClient.Message(content, refusal), finishReason))));
    }
}
