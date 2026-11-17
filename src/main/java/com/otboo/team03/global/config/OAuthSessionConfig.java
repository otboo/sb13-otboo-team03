package com.otboo.team03.global.config;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.SessionRepositoryFilter;

@Profile({"dev", "prod"})
@Configuration(proxyBeanMethods = false)
@EnableRedisHttpSession(redisNamespace = "otboo:oauth:session", maxInactiveIntervalInSeconds = 600)
public class OAuthSessionConfig {

    @Bean
    public FilterRegistrationBean<SessionRepositoryFilter<?>> oauthSessionFilterRegistration(
            @Qualifier("springSessionRepositoryFilter") SessionRepositoryFilter<?> sessionRepositoryFilter
    ) {
        FilterRegistrationBean<SessionRepositoryFilter<?>> registration =
                new FilterRegistrationBean<>(sessionRepositoryFilter);

        // OAuth 로그인 시작과 콜백에서만 Redis HTTP 세션을 사용한다.
        registration.addUrlPatterns("/oauth2/authorization/*", "/login/oauth2/code/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST);

        // OAuth 보안 필터가 세션을 읽기 전에 Redis 세션으로 감싼다.
        registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER);

        return registration;
    }
}