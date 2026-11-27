package com.otboo.team03.recommendation.rag;

import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class RecommendationRagPropertiesTest {
    @ParameterizedTest(name = "잘못된 설정 {0}={1}")
    @CsvSource({
            "jdbc-url, jdbc:mysql://localhost/references", "username, ''", "password, ''",
            "model, ''", "model-digest, invalid-digest", "ollama-url, ftp://localhost",
            "candidates, 0", "candidates, 101", "top-k, 0", "top-k, 11",
            "min-score, NaN", "min-score, -0.01", "min-score, 1.01", "max-context-characters, 0",
            "connect-timeout, 0s", "read-timeout, -1s", "retrieval-timeout, 0s",
            "cache-ttl, -1s", "cache-entries, 1", "cache-entries, 4097"
    })
    void 잘못된_연결_검색_설정은_시작할_때_거부한다(String key, String value) {
        assertThatThrownBy(() -> RagTestFixtures.properties(Map.of(key, value)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"1, 1, 0, 2", "100, 100, 1, 4096"})
    void 검색_개수_유사도_캐시의_경계값은_허용한다(int candidates, int topK, double score, int entries) {
        var properties = RagTestFixtures.properties(Map.of("candidates", candidates, "top-k", topK,
                "min-score", score, "cache-entries", entries));
        assertThat(properties.candidates()).isEqualTo(candidates);
        assertThat(properties.topK()).isEqualTo(topK);
        assertThat(properties.minScore()).isEqualTo(score);
        assertThat(properties.cacheEntries()).isEqualTo(entries);
    }
}
