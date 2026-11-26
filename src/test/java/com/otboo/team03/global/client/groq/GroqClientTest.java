package com.otboo.team03.global.client.groq;

import com.otboo.team03.global.exception.llm.GroqRequestException;
import com.otboo.team03.global.exception.llm.InvalidLlmResponseException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
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
    @ValueSource(strings = {"", "   "})
    void 빈_거부_문구는_정상_응답으로_처리한다(String refusal) {
        server.expect(anything()).andRespond(withSuccess(
                completion("답변", "stop", refusal), MediaType.APPLICATION_JSON));

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
    @ValueSource(strings = {
            "", "null", "{}", "{\"choices\":[]}", "{\"choices\":[null]}",
            "{\"choices\":[{},{}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\"}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{}}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"\"}}]}",
            "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"   \"}}]}"
    })
    void 응답_필수_값이_없으면_기본_추천으로_복구할_예외를_던진다(String response) {
        server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOf(InvalidLlmResponseException.class);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500})
    void HTTP_오류는_안전한_정보만_전달하고_원본_응답은_보관하지_않는다(int status) {
        String response = """
                {"error":{"type":"invalid_request_error","code":"json_validate_failed",
                  "message":"Bearer gsk_test_secret 의상제목",
                  "failed_generation":"의상제목과 개인 속성"}}
                """;
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body(response));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(GroqRequestException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(status);
                    assertThat(exception.getErrorType()).isEqualTo("invalid_request_error");
                    assertThat(exception.getErrorCode()).isEqualTo("json_validate_failed");
                    assertThat(exception.getMessage()).doesNotContain("gsk_test_secret", "의상제목");
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getSuppressed()).isEmpty();
                });
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "not json gsk_test_secret", "null", "{}", "{\"error\":null}",
            "{\"error\":{\"type\":{},\"code\":123}}",
            "{\"error\":{\"type\":\"gsk_test_secret\",\"code\":\"의상제목\"}}",
            "{\"error\":{\"type\":\"future_error\",\"code\":\"future_code\"}}",
            "{\"error\":{\"type\":\"invalid_request_error\\nBearer gsk_test_secret\",\"code\":\"json_validate_failed\\n의상제목\"}}"
    })
    void 오류_정보가_없거나_안전하지_않으면_상태만_보존한다(String response) {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(400))
                .contentType(MediaType.APPLICATION_JSON).body(response));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(GroqRequestException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(400);
                    assertThat(exception.getErrorType()).isEqualTo("unknown");
                    assertThat(exception.getErrorCode()).isEqualTo("unknown");
                    assertThat(exception.getCause()).isNull();
                });
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
            "invalid_request_error, missing-code, invalid_request_error, unknown",
            "missing-type, json_validate_failed, unknown, json_validate_failed",
            "rate_limit_error, rate_limit_exceeded, rate_limit_error, rate_limit_exceeded"
    })
    void 오류_종류와_코드는_각각_검증한다(String type, String code, String expectedType, String expectedCode) {
        String response = mapper.writeValueAsString(Map.of("error", Map.of("type", type, "code", code)));
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(400))
                .contentType(MediaType.APPLICATION_JSON).body(response));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(GroqRequestException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(expectedType);
                    assertThat(exception.getErrorCode()).isEqualTo(expectedCode);
                });
        server.verify();
    }

    @Test
    void HTML_오류_페이지도_본문을_전달하지_않고_상태를_보존한다() {
        server.expect(anything()).andRespond(withStatus(HttpStatusCode.valueOf(502))
                .contentType(MediaType.TEXT_HTML).body("<html>gsk_test_secret 의상제목</html>"));

        assertThatThrownBy(() -> client.complete(request()))
                .isInstanceOfSatisfying(GroqRequestException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(502);
                    assertThat(exception.getErrorType()).isEqualTo("unknown");
                    assertThat(exception.getErrorCode()).isEqualTo("unknown");
                    assertThat(exception.getMessage()).doesNotContain("gsk_test_secret", "의상제목");
                    assertThat(exception.getCause()).isNull();
                });
        server.verify();
    }

    private Map<String, Object> request() {
        return Map.of("model", "test-model", "messages", List.of(Map.of("role", "user", "content", "질문")));
    }

    private String completion(String content, String finishReason, String refusal) {
        return mapper.writeValueAsString(new GroqClient.Completion(List.of(
                new GroqClient.Choice(new GroqClient.Message(content, refusal), finishReason))));
    }
}
