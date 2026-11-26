package com.otboo.team03.recommendation.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRecommendationHistoryRepositoryTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @InjectMocks private RedisRecommendationHistoryRepository repository;

    private final UUID userId = UUID.randomUUID();
    private final UUID weatherId = UUID.randomUUID();
    private final UUID clothesId = UUID.randomUUID();
    private final String key = "recommendation:previous:" + userId + ":" + weatherId;

    @ParameterizedTest
    @MethodSource("recoverableFailures")
    void 조회_연결_장애나_시간_초과는_기록이_없는_것으로_처리한다(RuntimeException failure) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenThrow(failure);

        assertThat(repository.find(userId, weatherId)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("recoverableFailures")
    void 저장_연결_장애나_시간_초과는_추천_응답을_막지_않는다(RuntimeException failure) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(failure).when(valueOperations).set(key, clothesId.toString(), Duration.ofMinutes(30));

        assertThatCode(() -> repository.save(userId, weatherId, Set.of(clothesId)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("recoverableFailures")
    void 삭제_연결_장애나_시간_초과는_빈_추천_응답을_막지_않는다(RuntimeException failure) {
        when(redisTemplate.delete(key)).thenThrow(failure);

        assertThatCode(() -> repository.delete(userId, weatherId))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("unexpectedFailures")
    void 조회의_예상하지_못한_오류는_전파한다(RuntimeException failure) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenThrow(failure);

        assertThatThrownBy(() -> repository.find(userId, weatherId)).isSameAs(failure);
    }

    @ParameterizedTest
    @MethodSource("unexpectedFailures")
    void 저장의_예상하지_못한_오류는_전파한다(RuntimeException failure) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(failure).when(valueOperations).set(key, clothesId.toString(), Duration.ofMinutes(30));

        assertThatThrownBy(() -> repository.save(userId, weatherId, Set.of(clothesId))).isSameAs(failure);
    }

    @ParameterizedTest
    @MethodSource("unexpectedFailures")
    void 삭제의_예상하지_못한_오류는_전파한다(RuntimeException failure) {
        when(redisTemplate.delete(key)).thenThrow(failure);

        assertThatThrownBy(() -> repository.delete(userId, weatherId)).isSameAs(failure);
    }

    @Test
    void 저장된_의상_ID가_잘못되면_기록이_없는_것으로_숨기지_않는다() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(key)).thenReturn("invalid-uuid");

        assertThatThrownBy(() -> repository.find(userId, weatherId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Stream<RuntimeException> recoverableFailures() {
        return Stream.of(
                new RedisConnectionFailureException("Redis 연결 실패"),
                new QueryTimeoutException("Redis 응답 시간 초과"));
    }

    private static Stream<RuntimeException> unexpectedFailures() {
        return Stream.of(
                new RedisSystemException("예상하지 못한 Redis 오류", new IllegalStateException("원인")),
                new IllegalStateException("코드 오류"));
    }
}
