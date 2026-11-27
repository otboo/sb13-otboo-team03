package com.otboo.team03.recommendation.rag;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RecommendationRagConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(RecommendationRagConfig.class)
            .withBean(ObjectMapper.class, () -> JsonMapper.builder().build());

    @Test void 별도_활성화_설정_없이_RAG를_연결하고_업무_DataSource는_유지한다() {
        context.withBean("mainDataSource", DataSource.class, () -> mock(DataSource.class)).run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(DataSource.class).hasSingleBean(PgVectorReferenceStore.class);
            assertThat(ctx.getBean(OutfitReferenceProvider.class)).isInstanceOf(VectorOutfitReferenceProvider.class);
            assertThat(ctx.getBean(StyleReferenceCatalog.class).all()).hasSize(25);
        });
    }

    @Test void 잘못된_벡터DB_설정은_명확히_거부한다() {
        context.withPropertyValues("otboo.recommendation.rag.jdbc-url=jdbc:mysql://invalid")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
