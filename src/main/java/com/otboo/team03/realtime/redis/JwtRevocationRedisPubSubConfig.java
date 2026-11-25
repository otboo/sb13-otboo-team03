
package com.otboo.team03.realtime.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class JwtRevocationRedisPubSubConfig {

    @Bean(name = "jwtRevocationRedisChannel")
    ChannelTopic jwtRevocationRedisChannel(
            @Value("${otboo.redis.jwt-revocation-channel:otboo:jwt-revocations}") String channel
    ) {
        return new ChannelTopic(channel);
    }

    @Bean
    RedisMessageListenerContainer jwtRevocationRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            JwtRevocationRedisSubscriber subscriber,
            @Qualifier("jwtRevocationRedisChannel") ChannelTopic channel
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, channel);
        return container;
    }
}
