package com.otboo.team03.global.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Groq 공통 연결 설정. 기능별 모델·호출 정책은 각 도메인이 관리한다. */
@ConfigurationProperties(prefix = "groq")
public record GroqProperties(String apiKey, Duration connectTimeout, Duration readTimeout) {
    public GroqProperties {
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("Groq 연결·응답 시간 제한은 양수여야 합니다.");
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
