
package com.otboo.team03.user.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

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
}
