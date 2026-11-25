package com.otboo.team03.notification.outbox.relay;

import com.otboo.team03.global.config.JpaConfig;
import com.otboo.team03.global.config.QuerydslConfig;
import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.entity.OutboxEventStatus;
import com.otboo.team03.notification.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DataJpaTest
@Import({JpaConfig.class, QuerydslConfig.class})
@Testcontainers
class OutboxEventRelayConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 동시에_실행된_Relay는_이미_선점된_이벤트를_건너뛴다() throws Exception {
        OutboxEvent event = newEvent();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> outboxEventRepository.saveAndFlush(event));

        OutboxEventMessagePublisher messagePublisher = mock(OutboxEventMessagePublisher.class);
        CountDownLatch firstPublisherEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstPublisher = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstPublisherEntered.countDown();
            assertThat(releaseFirstPublisher.await(10, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(messagePublisher).publish(any(OutboxEvent.class));

        OutboxEventRelayService relayService = new OutboxEventRelayService(
                outboxEventRepository,
                messagePublisher
        );
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> firstRelay = executor.submit(() -> relay(transactionManager, relayService));
            assertThat(firstPublisherEntered.await(10, TimeUnit.SECONDS)).isTrue();

            Future<Integer> secondRelay = executor.submit(() -> relay(transactionManager, relayService));

            assertThat(secondRelay.get(10, TimeUnit.SECONDS)).isZero();

            releaseFirstPublisher.countDown();
            assertThat(firstRelay.get(10, TimeUnit.SECONDS)).isOne();
        } finally {
            releaseFirstPublisher.countDown();
            executor.shutdownNow();
        }

        verify(messagePublisher, times(1)).publish(any(OutboxEvent.class));
        assertThat(outboxEventRepository.findById(event.getEventId()))
                .get()
                .extracting(OutboxEvent::getStatus)
                .isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    private int relay(
            PlatformTransactionManager transactionManager,
            OutboxEventRelayService relayService
    ) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> relayService.relayPendingEvents(1));
    }

    private OutboxEvent newEvent() throws Exception {
        Instant now = Instant.now();
        return new OutboxEvent(
                UUID.randomUUID(),
                "DIRECT_MESSAGE_RECEIVED",
                (short) 1,
                "DIRECT_MESSAGE",
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                objectMapper.readTree("{\"content\":\"test\"}"),
                now,
                now
        );
    }
}
