package com.otboo.team03.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Profile({"dev", "prod"})
@Configuration(proxyBeanMethods = false)
@EnableRedisHttpSession(redisNamespace = "otboo:oauth:session", maxInactiveIntervalInSeconds = 600)
public class OAuthSessionConfig {
}