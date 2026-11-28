package com.otboo.team03.recommendation.rag;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 전용 풀을 내부에 소유하여 주 업무 DataSource·Flyway·트랜잭션 설정을 건드리지 않는다. */
public final class PgVectorReferenceStore implements AutoCloseable {
    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PgVectorReferenceStore(RecommendationRagProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.jdbcUrl());
        config.setUsername(properties.username());
        config.setPassword(properties.password());
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(1000);
        config.setValidationTimeout(500);
        config.setInitializationFailTimeout(-1);
        config.addDataSourceProperty("connectTimeout", "1");
        config.addDataSourceProperty("socketTimeout", "3");
        dataSource = new HikariDataSource(config);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(2);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }
    /** 자동 시작이나 추천 요청에서는 실행하지 않는다. 명시적 Java 인덱싱 명령 전용이다. */
    public void initialize() {
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS outfit_style_reference (
                    catalog_version text NOT NULL, embedding_model text NOT NULL, id text NOT NULL,
                    embedding vector(1024) NOT NULL,
                    PRIMARY KEY (catalog_version, embedding_model, id))
                """);
    }
    public void replace(String version, String model, List<StyleReference> references, List<float[]> vectors) {
        if (references.size() != vectors.size()) throw new ReferenceRetrievalException();
        List<String> values = vectors.stream().map(PgVectorReferenceStore::vector).toList();
        transaction.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM outfit_style_reference WHERE catalog_version=? AND embedding_model=?", version, model);
            for (int index = 0; index < references.size(); index++) {
                jdbc.update("INSERT INTO outfit_style_reference (catalog_version, embedding_model, id, embedding) VALUES (?, ?, ?, CAST(? AS vector))",
                        version, model, references.get(index).id(), values.get(index));
            }
        });
    }
    public List<Hit> search(String version, String model, float[] embedding, double minScore, int candidates) {
        String vector = vector(embedding);
        return jdbc.query("""
                SELECT id, 1 - (embedding <=> CAST(? AS vector)) AS score FROM outfit_style_reference
                WHERE catalog_version=? AND embedding_model=? AND 1 - (embedding <=> CAST(? AS vector)) >= ?
                ORDER BY embedding <=> CAST(? AS vector), id LIMIT ?
                """, (rs, row) -> new Hit(rs.getString("id"), rs.getDouble("score")),
                vector, version, model, vector, minScore, vector, candidates);
    }
    private static String vector(float[] embedding) {
        OllamaEmbeddingClient.validate(embedding);
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) result.append(',');
            result.append(Float.toString(embedding[index]));
        }
        return result.append(']').toString();
    }
    @Override public void close() { dataSource.close(); }
    public record Hit(String id, double score) { }
}
