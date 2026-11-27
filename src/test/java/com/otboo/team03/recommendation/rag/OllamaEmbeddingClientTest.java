package com.otboo.team03.recommendation.rag;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OllamaEmbeddingClientTest {
    private final Fixture fixture = new Fixture();
    private static class Fixture {
        final RecommendationRagProperties properties = RagTestFixtures.properties(Map.of());
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:11434");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        final OllamaEmbeddingClient client = new OllamaEmbeddingClient(builder.build(), properties);
    }
    private String tags(String digest) {
        return JsonMapper.builder().build().writeValueAsString(Map.of("models", List.of(Map.of(
                "name", fixture.properties.model(), "digest", digest, "size", 1157749568))));
    }
    private void expectTags(String digest) {
        fixture.server.expect(requestTo("http://localhost:11434/api/tags"))
                .andRespond(withSuccess(tags(digest), MediaType.APPLICATION_JSON));
    }
    @Test void 모델_digest와_차원을_확인하고_입력을_자르지_않는다() {
        expectTags(fixture.properties.modelDigest());
        fixture.server.expect(requestTo("http://localhost:11434/api/embed")).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.truncate").value(false)).andExpect(jsonPath("$.input[0]").value("옷장 설명"))
                .andRespond(withSuccess(JsonMapper.builder().build().writeValueAsString(Map.of(
                        "embeddings", List.of(RagTestFixtures.vector(0)), "total_duration", 123)), MediaType.APPLICATION_JSON));
        expectTags(fixture.properties.modelDigest());
        assertThat(fixture.client.embed(List.of("옷장 설명")).get(0)).hasSize(1024);
        fixture.server.verify();
    }
    @Test void digest가_다르면_서로_다른_모델의_벡터를_섞지_않는다() {
        expectTags("0".repeat(64));
        assertThatThrownBy(() -> fixture.client.embed(List.of("옷장"))).isInstanceOf(ReferenceRetrievalException.class);
        fixture.server.verify();
    }
    @Test void 차원이_다르거나_영벡터이면_거부한다() {
        for (float[] invalid : List.of(new float[1024], new float[3])) {
            expectTags(fixture.properties.modelDigest());
            fixture.server.expect(requestTo("http://localhost:11434/api/embed")).andRespond(withSuccess(
                    JsonMapper.builder().build().writeValueAsString(Map.of("embeddings", List.of(invalid))), MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> fixture.client.embed(List.of("옷장"))).isInstanceOf(ReferenceRetrievalException.class);
            fixture.server.verify(); fixture.server.reset();
        }
        float[] invalid = RagTestFixtures.vector(0); invalid[1] = Float.NaN;
        assertThatThrownBy(() -> OllamaEmbeddingClient.validate(invalid)).isInstanceOf(ReferenceRetrievalException.class);
    }
    @Test void 처리_도중_모델이_바뀌어도_거부한다() {
        expectTags(fixture.properties.modelDigest());
        fixture.server.expect(requestTo("http://localhost:11434/api/embed")).andRespond(withSuccess(
                JsonMapper.builder().build().writeValueAsString(Map.of("embeddings", List.of(RagTestFixtures.vector(0)))), MediaType.APPLICATION_JSON));
        expectTags("0".repeat(64));
        assertThatThrownBy(() -> fixture.client.embed(List.of("옷장"))).isInstanceOf(ReferenceRetrievalException.class);
    }
}
