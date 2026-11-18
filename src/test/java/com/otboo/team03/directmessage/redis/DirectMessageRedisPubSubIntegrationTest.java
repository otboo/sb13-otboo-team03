package com.otboo.team03.directmessage.redis;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.entity.DirectMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Testcontainers
class DirectMessageRedisPubSubIntegrationTest {

    private static final String CHANNEL = "otboo:direct-messages";
    private static final String SUBSCRIPTION_PREFIX = "/sub/direct-messages_";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory publisherConnectionFactory;
    private LettuceConnectionFactory subscriberConnectionFactoryA;
    private LettuceConnectionFactory subscriberConnectionFactoryB;
    private RedisMessageListenerContainer subscriberContainerA;
    private RedisMessageListenerContainer subscriberContainerB;
    private SimpMessagingTemplate messagingTemplateA;
    private SimpMessagingTemplate messagingTemplateB;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        publisherConnectionFactory = connectionFactory();
        subscriberConnectionFactoryA = connectionFactory();
        subscriberConnectionFactoryB = connectionFactory();

        messagingTemplateA = mock(SimpMessagingTemplate.class);
        messagingTemplateB = mock(SimpMessagingTemplate.class);
        objectMapper = new ObjectMapper();

        subscriberContainerA = subscriberContainer(
                subscriberConnectionFactoryA,
                new DirectMessageRedisSubscriber(objectMapper, messagingTemplateA)
        );
        subscriberContainerB = subscriberContainer(
                subscriberConnectionFactoryB,
                new DirectMessageRedisSubscriber(objectMapper, messagingTemplateB)
        );
        subscriberContainerA.start();
        subscriberContainerB.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        stop(subscriberContainerA);
        stop(subscriberContainerB);
        destroy(publisherConnectionFactory);
        destroy(subscriberConnectionFactoryA);
        destroy(subscriberConnectionFactoryB);
    }

    @Test
    void 하나의_Redis_DM_발행을_여러_인스턴스의_WebSocket으로_전달한다() throws InterruptedException {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(publisherConnectionFactory);
        DirectMessageRedisPublisher publisher = new DirectMessageRedisPublisher(
                redisTemplate,
                objectMapper,
                CHANNEL
        );
        DirectMessageDto directMessage = directMessage();
        String destination = SUBSCRIPTION_PREFIX + directMessage.dmKey();
        CountDownLatch delivered = new CountDownLatch(2);

        doAnswer(invocation -> {
            delivered.countDown();
            return null;
        }).when(messagingTemplateA).convertAndSend(destination, directMessage);
        doAnswer(invocation -> {
            delivered.countDown();
            return null;
        }).when(messagingTemplateB).convertAndSend(destination, directMessage);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (delivered.getCount() > 0 && System.nanoTime() < deadline) {
            publisher.publish(directMessage);
            delivered.await(100, TimeUnit.MILLISECONDS);
        }

        assertThat(delivered.getCount()).isZero();
        verify(messagingTemplateA).convertAndSend(destination, directMessage);
        verify(messagingTemplateB).convertAndSend(destination, directMessage);
    }

    private LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        factory.afterPropertiesSet();
        return factory;
    }

    private RedisMessageListenerContainer subscriberContainer(
            LettuceConnectionFactory connectionFactory,
            DirectMessageRedisSubscriber subscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(CHANNEL));
        container.afterPropertiesSet();
        return container;
    }

    private void stop(RedisMessageListenerContainer container) throws Exception {
        if (container != null) {
            container.stop();
            container.destroy();
        }
    }

    private void destroy(LettuceConnectionFactory connectionFactory) {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    private DirectMessageDto directMessage() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        return new DirectMessageDto(
                UUID.randomUUID(),
                Instant.parse("2026-11-18T00:00:00Z"),
                DirectMessage.createDmKey(senderId, receiverId),
                senderId,
                receiverId,
                "메시지"
        );
    }
}
