package com.otboo.team03.recommendation.rag;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("otboo.recommendation.rag")
public record RecommendationRagProperties(
        @DefaultValue("http://localhost:11434") URI ollamaUrl,
        @DefaultValue("bge-m3:latest") String model,
        @DefaultValue("7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab") String modelDigest,
        @DefaultValue("jdbc:postgresql://localhost:5433/outfit_references") String jdbcUrl,
        @DefaultValue("outfit_references") String username,
        @DefaultValue("outfit_reference_local") String password,
        @DefaultValue("1s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout,
        @DefaultValue("5s") Duration retrievalTimeout,
        @DefaultValue("10") int candidates,
        @DefaultValue("2") int topK,
        @DefaultValue("0.55") double minScore,
        @DefaultValue("2500") int maxContextCharacters,
        @DefaultValue("15m") Duration cacheTtl,
        @DefaultValue("256") int cacheEntries) {
    public static final int DIMENSIONS = 1024;

    public RecommendationRagProperties {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql:")
                || username == null || username.isBlank() || password == null || password.isBlank()
                || model == null || model.isBlank() || modelDigest == null || !modelDigest.matches("[a-f0-9]{64}")
                || ollamaUrl == null || !java.util.Set.of("http", "https").contains(ollamaUrl.getScheme())) {
            throw new IllegalArgumentException("RAG PostgreSQL·Ollama·모델 digest 설정을 확인하세요.");
        }
        if (candidates < 1 || candidates > 100 || topK < 1 || topK > candidates
                || !Double.isFinite(minScore) || minScore < 0 || minScore > 1 || maxContextCharacters < 1
                || !positive(connectTimeout) || !positive(readTimeout) || !positive(retrievalTimeout)
                || !positive(cacheTtl) || cacheEntries < 2 || cacheEntries > 4096) {
            throw new IllegalArgumentException("RAG 검색 개수·유사도·문맥 길이·시간 제한을 확인하세요.");
        }
    }

    private static boolean positive(Duration duration) {
        return duration != null && !duration.isNegative() && !duration.isZero();
    }

    public String modelKey() { return model + "@" + modelDigest; }
}
