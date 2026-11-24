package com.otboo.team03.realtime.redis;

import com.otboo.team03.realtime.RealtimeConnectionTerminationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Testcontainers
@DisplayName("JWT 무효화 Redis Pub/Sub 통합 테스트")
class JwtRevocationRedisPubSubIntegrationTest {

    private static final String CHANNEL = "otboo:jwt-revocations";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private LettuceConnectionFactory publisherConnectionFactory;
    private LettuceConnectionFactory subscriberConnectionFactoryA;
    private LettuceConnectionFactory subscriberConnectionFactoryB;

    private RedisMessageListenerContainer subscriberContainerA;
    private RedisMessageListenerContainer subscriberContainerB;

    private RealtimeConnectionTerminationService terminationServiceA;
    private RealtimeConnectionTerminationService terminationServiceB;

    @BeforeEach
    void setUp() {
        publisherConnectionFactory = connectionFactory();
        subscriberConnectionFactoryA = connectionFactory();
        subscriberConnectionFactoryB = connectionFactory();

        terminationServiceA = mock(RealtimeConnectionTerminationService.class);
        terminationServiceB = mock(RealtimeConnectionTerminationService.class);

        subscriberContainerA = subscriberContainer(
                subscriberConnectionFactoryA,
                new JwtRevocationRedisSubscriber(terminationServiceA)
        );
        subscriberContainerB = subscriberContainer(
                subscriberConnectionFactoryB,
                new JwtRevocationRedisSubscriber(terminationServiceB)
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

    @Nested
    @DisplayName("다중 인스턴스 이벤트 전파")
    class MultiInstanceTest {

        @Test
        @DisplayName("한 인스턴스에서 무효화 이벤트를 발행하면 모든 인스턴스의 실시간 연결을 종료한다")
        void publish_revocationEvent_disconnectsConnectionsOnAllInstances() throws InterruptedException {
            // given
            JwtRevocationRedisPublisher publisher = new JwtRevocationRedisPublisher(
                    new StringRedisTemplate(publisherConnectionFactory),
                    CHANNEL
            );

            UUID userId = UUID.randomUUID();
            CountDownLatch receivedA = new CountDownLatch(1);
            CountDownLatch receivedB = new CountDownLatch(1);

            doAnswer(invocation -> {
                receivedA.countDown();
                return null;
            }).when(terminationServiceA).disconnectAll(userId);

            doAnswer(invocation -> {
                receivedB.countDown();
                return null;
            }).when(terminationServiceB).disconnectAll(userId);

            // when
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

            while ((receivedA.getCount() > 0 || receivedB.getCount() > 0)
                    && System.nanoTime() < deadline) {
                publisher.publish(userId);
                receivedA.await(100, TimeUnit.MILLISECONDS);
                receivedB.await(100, TimeUnit.MILLISECONDS);
            }

            // then
            assertThat(receivedA.getCount()).isZero();
            assertThat(receivedB.getCount()).isZero();

            verify(terminationServiceA, atLeastOnce()).disconnectAll(userId);
            verify(terminationServiceB, atLeastOnce()).disconnectAll(userId);
        }
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
            JwtRevocationRedisSubscriber subscriber
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
}
