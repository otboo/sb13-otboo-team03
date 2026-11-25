package com.otboo.team03.recommendation.client;

import com.otboo.team03.global.client.groq.GroqClient;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static com.otboo.team03.recommendation.support.LlmTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GroqOutfitClientTest {
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private GroqOutfitClient client;

    @BeforeEach
    void setUp() throws IOException {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GroqOutfitClient(new GroqClient(builder.build()), mapper,
                new GroqOutfitRequestFactory(properties(true),
                        new ClassPathResource("prompts/outfit-recommendation-system.txt")));
    }

    @Test
    void 모든_제목과_자유_속성을_보내고_특징_이름을_사전으로_제한하지_않는다() {
        server.expect(requestTo("https://api.groq.com/openai/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(request -> {
                    var body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertThat(body.get("model").asText()).isEqualTo("openai/gpt-oss-120b");
                    assertThat(body.get("messages").get(0).get("role").asText()).isEqualTo("system");
                    assertThat(body.get("messages").get(0).get("content").asText())
                            .contains("비침, 크롭", "모든 제공 의상을 비교", "입력에 없는 사실을 만들어내지 않는다");
                    var input = mapper.readTree(body.get("messages").get(1).get("content").asText());
                    assertThat(input.get("wardrobe").size()).isEqualTo(3);
                    assertThat(input.get("wardrobe").get(0).get("name").asText()).isEqualTo("시스루 크롭 셔츠");
                    assertThat(input.get("wardrobe").get(0).get("attributes").get(0).get("name").asText())
                            .isEqualTo("원단 특징");
                    assertThat(input.get("wardrobe").get(0).get("attributes").get(0).get("value").asText())
                            .isEqualTo("비침 있음");
                    assertThat(input.get("weather").get("precipitationType").asText()).isEqualTo("RAIN");
                    assertThat(input.has("userId")).isFalse();
                    assertThat(input.toString()).doesNotContain("imageUrl", "profileImage", "email", "selectableValues");
                    var schema = body.get("response_format").get("json_schema");
                    assertThat(schema.get("strict").asBoolean()).isTrue();
                    assertThat(schema.get("schema").get("properties").get("clothesIds")
                            .get("items").get("enum").size()).isEqualTo(3);
                    var featureProperties = schema.get("schema").get("properties").get("analyses").get("items")
                            .get("properties").get("features").get("items").get("properties");
                    for (String field : List.of("name", "value")) {
                        assertThat(featureProperties.get(field).get("type").asText()).isEqualTo("string");
                        assertThat(featureProperties.get(field).has("enum")).isFalse();
                    }
                    var evidence = featureProperties.get("evidence");
                    assertThat(evidence.get("type").asText()).isEqualTo("string");
                    assertThat(evidence.get("description").asText())
                            .contains("TITLE", "ATTRIBUTE", "attributes[].value");
                }).andRespond(withSuccess(completion(mapper.writeValueAsString(decision()), "stop", null),
                        MediaType.APPLICATION_JSON));

        var actual = client.recommend(input(), client.serializeInput(input()));

        assertThat(actual).isEqualTo(decision());
        server.verify();
    }

    private String completion(String content, String finishReason, String refusal) {
        return mapper.writeValueAsString(new GroqClient.Completion(List.of(
                new GroqClient.Choice(new GroqClient.Message(content, refusal), finishReason))));
    }
}
