package com.otboo.team03.global.config;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class GroqPropertiesTest {
    private static final Duration VALID_TIMEOUT = Duration.ofSeconds(1);

    @ParameterizedTest
    @MethodSource("invalidTimeouts")
    void 연결과_응답_시간제한은_양수여야_한다(Duration connectTimeout, Duration readTimeout) {
        assertThatThrownBy(() -> new GroqProperties("key", connectTimeout, readTimeout))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Groq 연결·응답 시간 제한은 양수여야 합니다.");
    }

    static Stream<Arguments> invalidTimeouts() {
        return Stream.of(null, Duration.ZERO, Duration.ofMillis(-1))
                .flatMap(timeout -> Stream.of(
                        Arguments.of(timeout, VALID_TIMEOUT),
                        Arguments.of(VALID_TIMEOUT, timeout)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void 누락되거나_공백인_API키는_사용할_수_없다(String apiKey) {
        assertThat(new GroqProperties(apiKey, VALID_TIMEOUT, VALID_TIMEOUT).hasApiKey()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", " key "})
    void API키가_있으면_사용할_수_있다(String apiKey) {
        assertThat(new GroqProperties(apiKey, VALID_TIMEOUT, VALID_TIMEOUT).hasApiKey()).isTrue();
    }
}
