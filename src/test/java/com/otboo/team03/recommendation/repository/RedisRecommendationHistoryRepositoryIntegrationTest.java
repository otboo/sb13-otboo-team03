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
import java.util.List;
import java.util.ArrayList;
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

        assertThat(repository.findRecent(userId, weatherId)).containsExactly(outfit);
        assertThat(repository.findRecent(otherUserId, weatherId)).containsExactly(otherUserOutfit);
        assertThat(repository.findRecent(userId, otherWeatherId)).containsExactly(otherWeatherOutfit);
        assertThat(repository.findRecent(otherUserId, otherWeatherId)).isEmpty();
    }

    @Test
    void 기록을_갱신하면_직전_ID를_교체하고_보관_기간을_30분으로_다시_설정한다() {
        Set<UUID> previous = Set.of(UUID.randomUUID(), UUID.randomUUID());
        Set<UUID> latest = Set.of(UUID.randomUUID());
        repository.save(userId, weatherId, previous);
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1700L, 1800L);
        redisTemplate.expire(key, Duration.ofMinutes(1));
        redisTemplate.expire(recentKey(), Duration.ofMinutes(1));

        repository.save(userId, weatherId, latest);

        assertThat(repository.findRecent(userId, weatherId)).containsExactly(latest, previous);
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1700L, 1800L);
        assertThat(redisTemplate.getExpire(recentKey(), TimeUnit.SECONDS)).isBetween(1700L, 1800L);
    }

    @Test
    void 기록을_삭제해도_다른_날씨의_기록은_유지한다() {
        UUID otherWeatherId = UUID.randomUUID();
        Set<UUID> outfit = Set.of(UUID.randomUUID());
        repository.save(userId, weatherId, outfit);
        repository.save(userId, otherWeatherId, outfit);

        repository.delete(userId, weatherId);

        assertThat(repository.findRecent(userId, weatherId)).isEmpty();
        assertThat(repository.findRecent(userId, otherWeatherId)).containsExactly(outfit);
    }

    @Test
    void 기록이_없거나_만료되면_빈_이력을_반환한다() {
        assertThat(repository.findRecent(userId, weatherId)).isEmpty();
        repository.save(userId, weatherId, Set.of(UUID.randomUUID()));
        redisTemplate.expire(key, Duration.ZERO);
        redisTemplate.expire(recentKey(), Duration.ZERO);

        assertThat(repository.findRecent(userId, weatherId)).isEmpty();
    }

    @Test
    void 최근_다섯_코디만_최신순으로_원자적으로_보관한다() {
        List<Set<UUID>> outfits = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            Set<UUID> outfit = Set.of(UUID.randomUUID(), UUID.randomUUID());
            outfits.add(outfit); repository.save(userId, weatherId, outfit);
        }
        assertThat(repository.findRecent(userId, weatherId)).containsExactly(
                outfits.get(6), outfits.get(5), outfits.get(4), outfits.get(3), outfits.get(2));
        assertThat(redisTemplate.opsForList().size(recentKey())).isEqualTo(5L);
        assertThat(redisTemplate.getExpire(recentKey(), TimeUnit.SECONDS)).isBetween(1700L, 1800L);
    }

    @Test
    void 기존_문자열_기록을_읽고_이전_배포의_쓰기와도_호환한다() {
        Set<UUID> old = Set.of(UUID.randomUUID());
        redisTemplate.opsForValue().set(key, encode(old), Duration.ofMinutes(30));
        assertThat(repository.findRecent(userId, weatherId)).containsExactly(old);
        Set<UUID> current = Set.of(UUID.randomUUID()); repository.save(userId, weatherId, current);
        assertThat(repository.findRecent(userId, weatherId)).containsExactly(current, old);
        Set<UUID> oldWriter = Set.of(UUID.randomUUID());
        redisTemplate.opsForValue().set(key, encode(oldWriter), Duration.ofMinutes(30));
        assertThat(repository.findRecent(userId, weatherId)).containsExactly(oldWriter, current, old);
        Set<UUID> newest = Set.of(UUID.randomUUID()); repository.save(userId, weatherId, newest);
        assertThat(repository.findRecent(userId, weatherId)).containsExactly(newest, oldWriter, current, old);
    }

    @Test
    void 최근_기록도_조회로_TTL을_연장하지_않고_삭제시_두_형식을_지운다() {
        repository.save(userId, weatherId, Set.of(UUID.randomUUID()));
        redisTemplate.expire(key, Duration.ofMinutes(1)); redisTemplate.expire(recentKey(), Duration.ofMinutes(1));
        assertThat(repository.findRecent(userId, weatherId)).hasSize(1);
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 60L);
        assertThat(redisTemplate.getExpire(recentKey(), TimeUnit.SECONDS)).isBetween(1L, 60L);
        repository.delete(userId, weatherId);
        assertThat(redisTemplate.hasKey(key)).isFalse();
        assertThat(redisTemplate.hasKey(recentKey())).isFalse();
    }

    @Test
    void 손상된_기록이나_Redis_자료형_오류로_추천을_중단하지_않는다() {
        redisTemplate.opsForValue().set(key, "not-a-uuid", Duration.ofMinutes(30));
        assertThat(repository.findRecent(userId, weatherId)).isEmpty();
        redisTemplate.opsForValue().set(recentKey(), "wrong-type");
        assertThat(repository.findRecent(userId, weatherId)).isEmpty();
        repository.save(userId, weatherId, Set.of(UUID.randomUUID()));
        repository.delete(userId, weatherId);
    }

    private String recentKey() { return "recommendation:recent:" + userId + ":" + weatherId; }
    private String encode(Set<UUID> ids) { return ids.stream().map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining(",")); }
}
