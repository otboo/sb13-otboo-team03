package com.otboo.team03.recommendation.repository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisRecommendationHistoryRepositoryIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisRecommendationHistoryRepository repository;

    private final UUID userId = UUID.randomUUID();
    private final UUID weatherId = UUID.randomUUID();
    private final String key = "recommendation:previous:" + userId + ":" + weatherId;

    @BeforeAll
    static void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        repository = new RedisRecommendationHistoryRepository(redisTemplate);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void 사용자와_날씨별로_의상_ID_집합을_분리하여_저장한다() {
        UUID otherUserId = UUID.randomUUID();
        UUID otherWeatherId = UUID.randomUUID();
        Set<UUID> outfit = Set.of(UUID.randomUUID(), UUID.randomUUID());
        Set<UUID> otherUserOutfit = Set.of(UUID.randomUUID());
        Set<UUID> otherWeatherOutfit = Set.of(UUID.randomUUID());

        repository.save(userId, weatherId, outfit);
        repository.save(otherUserId, weatherId, otherUserOutfit);
        repository.save(userId, otherWeatherId, otherWeatherOutfit);

        assertThat(repository.find(userId, weatherId)).isEqualTo(outfit);
        assertThat(repository.find(otherUserId, weatherId)).isEqualTo(otherUserOutfit);
        assertThat(repository.find(userId, otherWeatherId)).isEqualTo(otherWeatherOutfit);
        assertThat(repository.find(otherUserId, otherWeatherId)).isEmpty();
    }

    @Test
    void 기록을_갱신하면_직전_ID를_교체하고_보관_기간을_30분으로_다시_설정한다() {
        Set<UUID> previous = Set.of(UUID.randomUUID(), UUID.randomUUID());
        Set<UUID> latest = Set.of(UUID.randomUUID());
        repository.save(userId, weatherId, previous);
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1700L, 1800L);
        redisTemplate.expire(key, Duration.ofMinutes(1));

        repository.save(userId, weatherId, latest);

        assertThat(repository.find(userId, weatherId)).isEqualTo(latest);
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1700L, 1800L);
    }

    @Test
    void 기록을_조회해도_보관_기간을_연장하지_않는다() {
        Set<UUID> outfit = Set.of(UUID.randomUUID());
        repository.save(userId, weatherId, outfit);
        redisTemplate.expire(key, Duration.ofMinutes(1));

        assertThat(repository.find(userId, weatherId)).isEqualTo(outfit);
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 60L);
    }

    @Test
    void 기록을_삭제해도_다른_날씨의_기록은_유지한다() {
        UUID otherWeatherId = UUID.randomUUID();
        Set<UUID> outfit = Set.of(UUID.randomUUID());
        repository.save(userId, weatherId, outfit);
        repository.save(userId, otherWeatherId, outfit);

        repository.delete(userId, weatherId);

        assertThat(repository.find(userId, weatherId)).isEmpty();
        assertThat(repository.find(userId, otherWeatherId)).isEqualTo(outfit);
    }

    @Test
    void 기록이_없거나_만료되면_빈_ID_집합을_반환한다() {
        assertThat(repository.find(userId, weatherId)).isEmpty();
        repository.save(userId, weatherId, Set.of(UUID.randomUUID()));
        redisTemplate.expire(key, Duration.ZERO);

        assertThat(repository.find(userId, weatherId)).isEmpty();
    }
}
