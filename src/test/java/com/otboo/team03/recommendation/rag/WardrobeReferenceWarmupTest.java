package com.otboo.team03.recommendation.rag;

import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.event.WardrobeChangedEvent;
import com.otboo.team03.recommendation.config.RecommendationLlmProperties;
import com.otboo.team03.recommendation.service.RecommendationInputReader;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static com.otboo.team03.recommendation.rag.RagTestFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WardrobeReferenceWarmupTest {
    @Test void 커밋한_옷장만_별도_스레드에서_읽고_저장_응답을_기다리게_하지_않는다() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (var fixture = new Fixture(true)) {
            var input = brownTopAndBlackPants(); UUID owner = UUID.randomUUID();
            when(fixture.reader.readForStyleReferences(owner)).thenAnswer(call -> {
                release.await(3, TimeUnit.SECONDS); return input;
            });
            assertTimeout(Duration.ofSeconds(1), () -> fixture.transaction.executeWithoutResult(status -> {
                fixture.context.publishEvent(new WardrobeChangedEvent(owner));
                verifyNoInteractions(fixture.reader, fixture.references);
            }));
            verify(fixture.reader, timeout(1000)).readForStyleReferences(owner);
            verifyNoInteractions(fixture.references);
            release.countDown();
            verify(fixture.references, timeout(1000)).prepare(input);
            verify(fixture.references, never()).find(any());
        } finally { release.countDown(); }
    }
    @Test void 롤백이나_트랜잭션_밖의_이벤트는_사전검색을_실행하지_않는다() {
        try (var fixture = new Fixture(true)) {
            UUID owner = UUID.randomUUID();
            fixture.transaction.executeWithoutResult(status -> {
                fixture.context.publishEvent(new WardrobeChangedEvent(owner)); status.setRollbackOnly();
            });
            fixture.context.publishEvent(new WardrobeChangedEvent(owner));
            verifyNoInteractions(fixture.reader, fixture.references);
        }
    }
    @Test void 기본조합이_없으면_검색하지_않는다() {
        try (var fixture = new Fixture(true)) {
            when(fixture.reader.readForStyleReferences(any())).thenReturn(input(List.of(item(ClothesType.TOP, "상의만 있음"))));
            fixture.publish();
            verify(fixture.reader, timeout(1000)).readForStyleReferences(any());
            verify(fixture.references, after(200).never()).prepare(any());
        }
    }
    @Test void LLM이_꺼져있으면_사전검색_빈도_만들지_않는다() {
        try (var fixture = new Fixture(false)) {
            assertThat(fixture.context.getBeansOfType(WardrobeReferenceWarmup.class)).isEmpty();
            fixture.publish(); verifyNoInteractions(fixture.reader, fixture.references);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final RecommendationInputReader reader;
        final OutfitReferenceProvider references;
        final TransactionTemplate transaction;
        Fixture(boolean enabled) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "otboo.recommendation.llm.enabled", enabled)));
            context.register(Config.class); context.refresh();
            reader = context.getBean(RecommendationInputReader.class);
            references = context.getBean(OutfitReferenceProvider.class);
            transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        }
        void publish() { transaction.executeWithoutResult(status -> context.publishEvent(new WardrobeChangedEvent(UUID.randomUUID()))); }
        public void close() { context.close(); }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import(WardrobeReferenceWarmup.class)
    static class Config {
        @Bean RecommendationInputReader reader() { return mock(RecommendationInputReader.class); }
        @Bean OutfitReferenceProvider references() { return mock(OutfitReferenceProvider.class); }
        @Bean RecommendationLlmProperties llm() { return new RecommendationLlmProperties(true, "test", 80, 12000, 2048); }
        @Bean ObjectMapper mapper() { return JsonMapper.builder().build(); }
        @Bean PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                protected Object doGetTransaction() { return new Object(); }
                protected void doBegin(Object transaction, TransactionDefinition definition) { }
                protected void doCommit(DefaultTransactionStatus status) { }
                protected void doRollback(DefaultTransactionStatus status) { }
            };
        }
    }
}
