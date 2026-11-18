package com.otboo.team03.notification.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationRedisPubSubConfigTest {

    private static final String CHANNEL = "otboo:notifications";

    @Test
    void 알림_Redis_채널을_생성한다() {
        NotificationRedisPubSubConfig config = new NotificationRedisPubSubConfig();

        ChannelTopic topic = config.notificationRedisChannel(CHANNEL);

        assertThat(topic.getTopic()).isEqualTo(CHANNEL);
    }

    @Test
    void Redis_메시지_리스너_컨테이너를_생성하고_연결한다() {
        NotificationRedisPubSubConfig config = new NotificationRedisPubSubConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        NotificationRedisSubscriber subscriber = mock(NotificationRedisSubscriber.class);
        ChannelTopic topic = new ChannelTopic(CHANNEL);

        RedisMessageListenerContainer container = config.notificationRedisMessageListenerContainer(
                connectionFactory,
                subscriber,
                topic
        );

        assertThat(container).isNotNull();
        assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
    }
}
