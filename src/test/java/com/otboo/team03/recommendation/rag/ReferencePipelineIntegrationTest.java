package com.otboo.team03.recommendation.rag;

import com.otboo.team03.global.client.groq.GroqClient;
import com.otboo.team03.recommendation.client.GroqOutfitClient;
import com.otboo.team03.recommendation.client.GroqOutfitRequestFactory;
import com.otboo.team03.recommendation.client.dto.LlmOutfitDecision;
import com.otboo.team03.recommendation.client.dto.LlmRecommendationInput;
import com.otboo.team03.recommendation.config.RecommendationLlmProperties;
import com.otboo.team03.recommendation.service.LlmOutfitRecommender;
import com.otboo.team03.recommendation.validation.LlmOutfitValidator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@Testcontainers
class ReferencePipelineIntegrationTest {
    @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres"));
    private final tools.jackson.databind.ObjectMapper mapper = JsonMapper.builder().build();
    private RecommendationRagProperties properties;
    private StyleReferenceCatalog catalog;
    private PgVectorReferenceStore store;

    @BeforeEach void setup() throws Exception {
        properties = RagTestFixtures.properties(Map.of("jdbc-url", DATABASE.getJdbcUrl(), "username", DATABASE.getUsername(),
                "password", DATABASE.getPassword()));
        catalog = RagTestFixtures.catalog(); store = new PgVectorReferenceStore(properties); store.initialize();
        float[] similar = new float[1024]; similar[0] = .8f; similar[1] = .6f;
        store.replace(catalog.version(), properties.modelKey(), List.of(catalog.get("uniqlo-brown-black"),
                catalog.get("musinsa-cropped-curve"), catalog.get("musinsa-shirt-trousers")),
                List.of(RagTestFixtures.vector(0), RagTestFixtures.vector(1), similar));
    }
    @AfterEach void close() { if (store != null) store.close(); }

    @Test void 실제_pgvector에서_코사인_순위와_모델_자료버전_격리를_확인한다() {
        assertThat(store.search(catalog.version(), properties.modelKey(), RagTestFixtures.vector(0), .55, 10))
                .extracting(PgVectorReferenceStore.Hit::id).containsExactly("uniqlo-brown-black", "musinsa-shirt-trousers");
        assertThat(store.search("stale", properties.modelKey(), RagTestFixtures.vector(0), .55, 10)).isEmpty();
        assertThat(store.search(catalog.version(), "other-model", RagTestFixtures.vector(0), .55, 10)).isEmpty();
    }
    @Test void 인덱스_교체가_실패하면_기존_벡터가_롤백으로_보존된다() {
        var duplicate = catalog.get("uniqlo-brown-black");
        assertThatThrownBy(() -> store.replace(catalog.version(), properties.modelKey(), List.of(duplicate, duplicate),
                List.of(RagTestFixtures.vector(1), RagTestFixtures.vector(1)))).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(store.search(catalog.version(), properties.modelKey(), RagTestFixtures.vector(0), .99, 10))
                .extracting(PgVectorReferenceStore.Hit::id).containsExactly("uniqlo-brown-black");
    }
    @Test void 벡터검색_문맥이_Groq_요청에_포함되어도_소유권_스키마와_날씨는_유지된다() throws Exception {
        exercisePipeline(false);
    }
    @Test void 검색_HTTP_장애에도_기존_LLM_요청으로_복구한다() throws Exception {
        exercisePipeline(true);
    }
    @Test void 인덱싱_명령은_업무DB_Redis_Groq_없이_자료_전체를_저장한다() {
        try (var embeddings = mockConstruction(OllamaEmbeddingClient.class, (client, context) ->
                when(client.embed(anyList())).thenAnswer(call ->
                        java.util.Collections.nCopies(((List<?>) call.getArgument(0)).size(), RagTestFixtures.vector(0))))) {
            ReferenceIndexCommand.main(new String[]{
                    "--spring.config.import=", "--spring.profiles.active=rag-index",
                    "--otboo.recommendation.rag.jdbc-url=" + DATABASE.getJdbcUrl(),
                    "--otboo.recommendation.rag.username=" + DATABASE.getUsername(),
                    "--otboo.recommendation.rag.password=" + DATABASE.getPassword()
            });
            assertThat(embeddings.constructed()).hasSize(1);
            assertThat(store.search(catalog.version(), properties.modelKey(), RagTestFixtures.vector(0), .99, 100))
                    .extracting(PgVectorReferenceStore.Hit::id)
                    .containsExactlyInAnyOrderElementsOf(catalog.all().stream().map(StyleReference::id).toList());
        }
    }
    private void exercisePipeline(boolean embeddingFailure) throws Exception {
        RestClient.Builder ollama = RestClient.builder().baseUrl("http://ollama.test");
        var embeddingServer = MockRestServiceServer.bindTo(ollama).build();
        String tags = mapper.writeValueAsString(Map.of("models", List.of(Map.of("name", properties.model(), "digest", properties.modelDigest()))));
        embeddingServer.expect(requestTo("http://ollama.test/api/tags")).andRespond(embeddingFailure ? withServerError()
                : withSuccess(tags, MediaType.APPLICATION_JSON));
        if (!embeddingFailure) {
            embeddingServer.expect(requestTo("http://ollama.test/api/embed")).andRespond(withSuccess(
                    mapper.writeValueAsString(Map.of("embeddings", List.of(RagTestFixtures.vector(0)))), MediaType.APPLICATION_JSON));
            embeddingServer.expect(requestTo("http://ollama.test/api/tags")).andRespond(withSuccess(tags, MediaType.APPLICATION_JSON));
        }
        var items = RagTestFixtures.brownTopAndBlackPants().wardrobe();
        var input = new LlmRecommendationInput(new LlmRecommendationInput.WeatherContext(5.0, null, null, null, null, null,
                LocalDate.of(2026, 4, 10)), 1, Set.of(items.get(0).clothesId()), items, List.of());
        var decision = new LlmOutfitDecision(items.stream().map(LlmRecommendationInput.Item::clothesId).toList());
        RestClient.Builder groq = RestClient.builder().baseUrl("https://groq.test");
        var groqServer = MockRestServiceServer.bindTo(groq).build();
        groqServer.expect(requestTo("https://groq.test/chat/completions")).andExpect(request -> {
            var body = mapper.readTree(((MockClientHttpRequest) request).getBodyAsString());
            var messages = body.get("messages");
            assertThat(messages.size()).isEqualTo(embeddingFailure ? 2 : 3);
            var original = mapper.readTree(messages.get(1).get("content").asText());
            assertThat(original.get("wardrobe").size()).isEqualTo(2);
            assertThat(original.get("weather").get("forecastDate").asText()).isEqualTo("2026-04-10");
            assertThat(original.get("adjustedTemperature").asDouble()).isEqualTo(1.0);
            if (!embeddingFailure) {
                var references = mapper.readTree(messages.get(2).get("content").asText()).get("styleReferences");
                assertThat(references.get(0).get("id").asText()).isEqualTo("uniqlo-brown-black");
                assertThat(messages.get(0).get("content").asText()).contains("자료가 옷장·날씨에 맞지 않으면 무시");
            } else assertThat(messages.get(0).get("content").asText()).doesNotContain("styleReferences");
            var allowed = body.get("response_format").get("json_schema").get("schema").get("properties").get("clothesIds").get("items").get("enum");
            assertThat(allowed).isEqualTo(mapper.readTree(mapper.writeValueAsString(decision.clothesIds())));
        }).andRespond(withSuccess(mapper.writeValueAsString(Map.of("choices", List.of(Map.of("message",
                Map.of("content", mapper.writeValueAsString(decision)), "finish_reason", "stop")))), MediaType.APPLICATION_JSON));
        var llmProperties = new RecommendationLlmProperties(true, "test-model", 80, 12000, 2048);
        var client = new GroqOutfitClient(new GroqClient(groq.build()), mapper,
                new GroqOutfitRequestFactory(llmProperties, new ClassPathResource("prompts/outfit-recommendation-system.txt")));
        try (var provider = new VectorOutfitReferenceProvider(properties, catalog, new OllamaEmbeddingClient(ollama.build(), properties),
                store, new ReferenceCompatibility(), mapper, null)) {
            assertThat(new LlmOutfitRecommender(llmProperties, client, new LlmOutfitValidator(), provider).recommend(input)).contains(decision);
        }
        embeddingServer.verify(); groqServer.verify();
    }
}
