package com.otboo.team03.directmessage.redis;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class DirectMessageRedisPubSubConfig {

    @Bean(name = "directMessageRedisChannel")
    ChannelTopic directMessageRedisChannel(
            @Value("${otboo.redis.direct-message-channel:otboo:direct-messages}") String channel
    ) {
        return new ChannelTopic(channel);
    }

    @Bean
    RedisMessageListenerContainer directMessageRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            DirectMessageRedisSubscriber subscriber,
            @Qualifier("directMessageRedisChannel") ChannelTopic directMessageRedisChannel
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, directMessageRedisChannel);
        return container;
    }
}
