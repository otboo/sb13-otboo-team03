package com.otboo.team03.global.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(CacheConfig.CacheProperties.class)
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            GenericJacksonJsonRedisSerializer redisSerializer,
            CacheProperties properties
    ) {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.defaultTtl())
                .disableCachingNullValues()
                .prefixCacheNameWith(CacheNames.KEY_PREFIX)
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(redisSerializer));

        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .transactionAware();

        properties.ttls().forEach((name, ttl) -> builder.withCacheConfiguration(name, defaults.entryTtl(ttl)));

        return builder.build();
    }

    @ConfigurationProperties(prefix = "otboo.cache")
    public record CacheProperties(Duration defaultTtl, Map<String, Duration> ttls) {

        public CacheProperties {
            defaultTtl = defaultTtl == null ? Duration.ofMinutes(5) : defaultTtl;
            ttls = ttls == null ? Map.of() : Map.copyOf(ttls);
        }
    }
}