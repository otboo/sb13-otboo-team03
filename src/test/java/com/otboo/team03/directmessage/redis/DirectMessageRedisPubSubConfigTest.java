package com.otboo.team03.directmessage.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DirectMessageRedisPubSubConfigTest {

    private static final String CHANNEL = "otboo:direct-messages";

    @Test
    void DM_Redis_채널을_생성한다() {
        DirectMessageRedisPubSubConfig config = new DirectMessageRedisPubSubConfig();

        ChannelTopic topic = config.directMessageRedisChannel(CHANNEL);

        assertThat(topic.getTopic()).isEqualTo(CHANNEL);
    }

    @Test
    void Redis_메시지_리스너_컨테이너를_생성하고_연결한다() {
        DirectMessageRedisPubSubConfig config = new DirectMessageRedisPubSubConfig();
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        DirectMessageRedisSubscriber subscriber = mock(DirectMessageRedisSubscriber.class);
        ChannelTopic topic = new ChannelTopic(CHANNEL);

        RedisMessageListenerContainer container = config.directMessageRedisMessageListenerContainer(
                connectionFactory,
                subscriber,
                topic
        );

        assertThat(container).isNotNull();
        assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
    }
}
