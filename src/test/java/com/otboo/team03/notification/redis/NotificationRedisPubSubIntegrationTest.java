package com.otboo.team03.notification.redis;

import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.sse.SseConnectionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
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
class NotificationRedisPubSubIntegrationTest {

    private static final String CHANNEL = "otboo:notifications";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory publisherConnectionFactory;
    private LettuceConnectionFactory subscriberConnectionFactoryA;
    private LettuceConnectionFactory subscriberConnectionFactoryB;
    private RedisMessageListenerContainer subscriberContainerA;
    private RedisMessageListenerContainer subscriberContainerB;
    private SseConnectionManager sseConnectionManagerA;
    private SseConnectionManager sseConnectionManagerB;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        publisherConnectionFactory = connectionFactory();
        subscriberConnectionFactoryA = connectionFactory();
        subscriberConnectionFactoryB = connectionFactory();

        sseConnectionManagerA = mock(SseConnectionManager.class);
        sseConnectionManagerB = mock(SseConnectionManager.class);
        objectMapper = new ObjectMapper();

        subscriberContainerA = subscriberContainer(
                subscriberConnectionFactoryA,
                new NotificationRedisSubscriber(objectMapper, sseConnectionManagerA)
        );
        subscriberContainerB = subscriberContainer(
                subscriberConnectionFactoryB,
                new NotificationRedisSubscriber(objectMapper, sseConnectionManagerB)
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
    void 하나의_Redis_발행을_여러_인스턴스의_SSE로_전달한다() throws InterruptedException {
        StringRedisTemplate redisTemplate = new StringRedisTemplate(publisherConnectionFactory);
        NotificationRedisPublisher publisher = new NotificationRedisPublisher(
                redisTemplate,
                objectMapper,
                CHANNEL
        );
        NotificationDto notification = notification();
        CountDownLatch delivered = new CountDownLatch(2);

        doAnswer(invocation -> {
            delivered.countDown();
            return null;
        }).when(sseConnectionManagerA).send(notification.receiverId(), notification);
        doAnswer(invocation -> {
            delivered.countDown();
            return null;
        }).when(sseConnectionManagerB).send(notification.receiverId(), notification);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (delivered.getCount() > 0 && System.nanoTime() < deadline) {
            publisher.publish(notification);
            delivered.await(100, TimeUnit.MILLISECONDS);
        }

        assertThat(delivered.getCount()).isZero();
        verify(sseConnectionManagerA).send(eq(notification.receiverId()), eq(notification));
        verify(sseConnectionManagerB).send(eq(notification.receiverId()), eq(notification));
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
            NotificationRedisSubscriber subscriber
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

    private NotificationDto notification() {
        return new NotificationDto(
                UUID.randomUUID(),
                Instant.parse("2026-11-18T00:00:00Z"),
                UUID.randomUUID(),
                "알림",
                "알림 내용",
                NotificationLevel.INFO
        );
    }
}
