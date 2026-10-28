
package com.otboo.team03.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJacksonJsonRedisSerializer redisSerializer) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        template.setValueSerializer(redisSerializer);
        template.setHashValueSerializer(redisSerializer);

        template.afterPropertiesSet();

        return template;
    }

    @Bean
    public GenericJacksonJsonRedisSerializer redisSerializer() {

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.otboo.team03.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .build();

        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(ptv)
                .build();
    }
}
