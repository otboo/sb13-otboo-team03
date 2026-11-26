package com.otboo.team03.recommendation.config;

import com.otboo.team03.global.config.GroqProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 추천 기능 활성화 시 필요한 공통 연결 설정을 확인한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecommendationLlmProperties.class)
public class RecommendationLlmConfig {
    public RecommendationLlmConfig(RecommendationLlmProperties properties, GroqProperties groqProperties) {
        if (properties.enabled() && !groqProperties.hasApiKey()) {
            throw new IllegalArgumentException("LLM 추천 활성화 시 Groq API 키가 필요합니다.");
        }
    }
}
