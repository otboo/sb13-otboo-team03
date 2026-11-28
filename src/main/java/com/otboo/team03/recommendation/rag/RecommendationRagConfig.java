package com.otboo.team03.recommendation.rag;

import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Every LLM recommendation uses the reference search provider; no separate RAG switch. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecommendationRagProperties.class)
public class RecommendationRagConfig {
    @Bean StyleReferenceCatalog styleReferenceCatalog() throws IOException {
        return new StyleReferenceCatalog(new ClassPathResource("rag/outfit-style-references.tsv"));
    }
    @Bean ReferenceCompatibility referenceCompatibility() { return new ReferenceCompatibility(); }
    @Bean OllamaEmbeddingClient ollamaEmbeddingClient(RecommendationRagProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return new OllamaEmbeddingClient(RestClient.builder().baseUrl(properties.ollamaUrl().toString())
                .requestFactory(factory).build(), properties);
    }
    @Bean(destroyMethod = "close") PgVectorReferenceStore pgVectorReferenceStore(RecommendationRagProperties properties) {
        return new PgVectorReferenceStore(properties);
    }
    @Bean(destroyMethod = "close") OutfitReferenceProvider outfitReferenceProvider(RecommendationRagProperties properties,
            StyleReferenceCatalog catalog, OllamaEmbeddingClient embeddings, PgVectorReferenceStore store,
            ReferenceCompatibility compatibility, ObjectMapper mapper, ObjectProvider<StringRedisTemplate> redis) {
        var cache = new RedisOutfitReferenceCache(redis.getIfAvailable(), mapper, properties);
        return new VectorOutfitReferenceProvider(properties, catalog, embeddings, store, compatibility, mapper, cache);
    }
}
