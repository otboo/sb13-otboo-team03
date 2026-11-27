package com.otboo.team03.recommendation.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

public final class OllamaEmbeddingClient {
    private final RestClient client;
    private final RecommendationRagProperties properties;

    public OllamaEmbeddingClient(RestClient client, RecommendationRagProperties properties) {
        this.client = client;
        this.properties = properties;
    }
    public List<float[]> embed(List<String> texts) {
        verifyModel();
        if (texts.isEmpty() || texts.stream().anyMatch(String::isBlank)) throw new ReferenceRetrievalException();
        EmbedResponse response = client.post().uri("/api/embed")
                .body(Map.of("model", properties.model(), "input", texts, "truncate", false, "keep_alive", "30m"))
                .retrieve().body(EmbedResponse.class);
        if (response == null || response.embeddings() == null || response.embeddings().size() != texts.size()) {
            throw new ReferenceRetrievalException();
        }
        response.embeddings().forEach(OllamaEmbeddingClient::validate);
        // Alias changes must not mix embeddings from different model weights.
        verifyModel();
        if (Thread.currentThread().isInterrupted()) throw new ReferenceRetrievalException();
        return response.embeddings();
    }
    private void verifyModel() {
        if (Thread.currentThread().isInterrupted()) throw new ReferenceRetrievalException();
        Models response = client.get().uri("/api/tags").retrieve().body(Models.class);
        if (response == null || response.models() == null || response.models().stream().noneMatch(model ->
                model != null && properties.model().equals(model.name()) && properties.modelDigest().equals(model.digest()))) {
            throw new ReferenceRetrievalException();
        }
    }
    static void validate(float[] vector) {
        if (vector == null || vector.length != RecommendationRagProperties.DIMENSIONS) throw new ReferenceRetrievalException();
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new ReferenceRetrievalException();
            norm += (double) value * value;
        }
        if (norm == 0) throw new ReferenceRetrievalException();
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbedResponse(List<float[]> embeddings) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Models(List<Model> models) { }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Model(String name, String digest) { }
}
