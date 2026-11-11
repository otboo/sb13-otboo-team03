package com.otboo.team03.user.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisTemporaryPasswordRepositoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RedisTemporaryPasswordRepository repository;

    @Nested
    @DisplayName("임시 비밀번호 저장")
    class SaveTest {

        @Test
        @DisplayName("임시 비밀번호 해시를 3분 만료시간으로 저장한다")
        void save_storesPasswordWithThreeMinuteTtl() {
            // given
            String email = "user@example.com";
            String encodedPassword = "encodedTemporaryPassword";
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            // when
            repository.save(email, encodedPassword);

            // then
            verify(valueOperations).set(
                    "auth:temp-password:" + email,
                    encodedPassword,
                    Duration.ofMinutes(3)
            );
        }
    }

    @Nested
    @DisplayName("임시 비밀번호 조회")
    class FindByEmailTest {

        @Test
        @DisplayName("저장된 임시 비밀번호 해시를 조회한다")
        void findByEmail_existingPassword_returnsPassword() {
            // given
            String email = "user@example.com";
            String encodedPassword = "encodedTemporaryPassword";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("auth:temp-password:" + email)).thenReturn(encodedPassword);

            // when
            Optional<String> result = repository.findByEmail(email);

            // then
            assertThat(result).contains(encodedPassword);
        }

        @Test
        @DisplayName("임시 비밀번호가 없거나 만료되면 빈 결과를 반환한다")
        void findByEmail_missingPassword_returnsEmpty() {
            // given
            String email = "user@example.com";

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("auth:temp-password:" + email)).thenReturn(null);

            // when
            Optional<String> result = repository.findByEmail(email);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("임시 비밀번호 삭제")
    class DeleteTest {

        @Test
        @DisplayName("이메일에 해당하는 임시 비밀번호를 삭제한다")
        void delete_removesTemporaryPassword() {
            // given
            String email = "user@example.com";

            // when
            repository.delete(email);

            // then
            verify(redisTemplate).delete("auth:temp-password:" + email);
        }
    }

    @Nested
    @DisplayName("임시 비밀번호 일회성 사용")
    class ConsumeIfMatchesTest {

        @Test
        @DisplayName("성공 - 저장된 해시가 일치하면 임시 비밀번호를 소모한다")
        void consumeIfMatches_matchingPassword_returnsTrue() {
            // given
            String email = "user@example.com";
            String encodedPassword = "encodedTemporaryPassword";
            String key = "auth:temp-password:" + email;

            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(key)),
                    eq(encodedPassword)
            )).thenReturn(1L);

            // when
            boolean result = repository.consumeIfMatches(email, encodedPassword);

            // then
            assertThat(result).isTrue();
            verify(redisTemplate).execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of(key)),
                    eq(encodedPassword)
            );
        }

        @Test
        @DisplayName("실패 - 저장된 해시가 일치하지 않으면 소모하지 않는다")
        void consumeIfMatches_wrongPassword_returnsFalse() {
            // given
            String email = "user@example.com";
            String encodedPassword = "wrongEncodedPassword";

            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of("auth:temp-password:" + email)),
                    eq(encodedPassword)
            )).thenReturn(0L);

            // when
            boolean result = repository.consumeIfMatches(email, encodedPassword);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("실패 - 임시 비밀번호가 없으면 소모하지 않는다")
        void consumeIfMatches_missingPassword_returnsFalse() {
            // given
            String email = "user@example.com";
            String encodedPassword = "encodedTemporaryPassword";

            when(redisTemplate.execute(
                    ArgumentMatchers.<RedisScript<Long>>any(),
                    eq(List.of("auth:temp-password:" + email)),
                    eq(encodedPassword)
            )).thenReturn(0L);

            // when
            boolean result = repository.consumeIfMatches(email, encodedPassword);

            // then
            assertThat(result).isFalse();
        }
    }
}
