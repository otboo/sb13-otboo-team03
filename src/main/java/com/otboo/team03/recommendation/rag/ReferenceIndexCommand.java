package com.otboo.team03.recommendation.rag;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** 업무 DB·Groq·웹 서버 없이 실행하는 별도 Java 명령. */
public final class ReferenceIndexCommand {
    public static void main(String[] args) {
        try (var context = new SpringApplicationBuilder(IndexConfiguration.class).web(WebApplicationType.NONE)
                .profiles("rag-index").properties("spring.config.import=optional:file:./.env[.properties]").run(args)) {
            var properties = context.getBean(RecommendationRagProperties.class);
            int count = new StyleReferenceIndexer(context.getBean(StyleReferenceCatalog.class),
                    context.getBean(OllamaEmbeddingClient.class), context.getBean(PgVectorReferenceStore.class), properties).rebuild();
            System.out.println("스타일 자료 인덱싱 완료: " + count);
        }
    }
    @Configuration(proxyBeanMethods = false)
    @Profile("rag-index")
    @Import(RecommendationRagConfig.class)
    static class IndexConfiguration {
        @Bean ObjectMapper objectMapper() { return JsonMapper.builder().build(); }
    }
}
