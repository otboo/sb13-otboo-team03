package com.otboo.team03.notification.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class NotificationRedisPubSubConfig {

    @Bean
    ChannelTopic notificationRedisChannel(
            @Value("${otboo.redis.notification-channel:otboo:notifications}") String channel
    ) {
        return new ChannelTopic(channel);
    }

    @Bean
    RedisMessageListenerContainer notificationRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationRedisSubscriber subscriber,
            ChannelTopic notificationRedisChannel
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, notificationRedisChannel);
        return container;
    }
}
