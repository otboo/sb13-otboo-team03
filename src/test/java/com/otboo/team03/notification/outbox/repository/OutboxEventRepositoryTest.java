package com.otboo.team03.notification.outbox.repository;

import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.SessionEventListener;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.outbox.service.OutboxEventService;
import com.otboo.team03.global.config.JpaConfig;
import com.otboo.team03.global.config.QuerydslConfig;
import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.entity.OutboxEventStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DataJpaTest
@Import({JpaConfig.class, QuerydslConfig.class})
@Testcontainers
class OutboxEventRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 신규_이벤트_51건을_사전조회_없이_두_배치로_저장한다() {
        var publisher = new OutboxEventService(entityManager, objectMapper);
        var listener = mock(SessionEventListener.class);
        entityManager.unwrap(Session.class).addEventListeners(listener);
        UUID definitionId = UUID.randomUUID();
        Instant now = Instant.now();
        for (int i = 0; i < 51; i++) {
            UUID receiver = UUID.randomUUID();
            var event = new NotificationEvent(UUID.randomUUID(), NotificationEventType.CLOTHES_ATTRIBUTE_CREATED,
                    NotificationEvent.CURRENT_SCHEMA_VERSION, now, definitionId, receiver, null,
                    definitionId, "새 의상 속성", "소재", NotificationLevel.INFO);
            publisher.publish(event, "CLOTHES_ATTRIBUTE_DEFINITION", receiver.toString());
        }
        entityManager.flush();

        verify(listener, never()).jdbcExecuteStatementEnd();
        verify(listener, times(2)).jdbcExecuteBatchEnd();
        assertThat(entityManager.createQuery("select count(e) from OutboxEvent e where e.aggregateId = :id", Long.class)
                .setParameter("id", definitionId).getSingleResult()).isEqualTo(51L);
    }

    @Test
    void 아웃박스_이벤트를_JSON_payload와_함께_저장하고_조회한다() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-11-11T23:59:00Z");
        Instant createdAt = Instant.parse("2026-11-12T00:00:00Z");
        OutboxEvent event = new OutboxEvent(
                eventId,
                "FEED_CREATED",
                (short) 1,
                "FEED",
                aggregateId,
                "receiver-1",
                objectMapper.readTree("{\"receiverId\":\"receiver-1\"}"),
                occurredAt,
                createdAt
        );

        OutboxEvent saved = outboxEventRepository.saveAndFlush(event);

        assertThat(outboxEventRepository.findById(eventId)).get()
                .satisfies(found -> {
                    assertThat(found.getEventId()).isEqualTo(eventId);
                    assertThat(found.getAggregateId()).isEqualTo(aggregateId);
                    assertThat(found.getOccurredAt()).isEqualTo(occurredAt);
                    assertThat(found.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
                    assertThat(found.getAttemptCount()).isZero();
                    assertThat(found.getAvailableAt()).isEqualTo(createdAt);
                    assertThat(found.getCreatedAt()).isEqualTo(createdAt);
                    assertThat(found.getPayload().get("receiverId").asText()).isEqualTo("receiver-1");
                });
        assertThat(saved.getEventId()).isEqualTo(eventId);
    }

    @Test
    void 발행_대기_시각이_지난_PENDING_이벤트를_생성시각_순으로_조회한다() throws Exception {
        Instant availableAt = Instant.parse("2026-11-12T00:00:00Z");
        OutboxEvent later = newEvent(availableAt.plusSeconds(2), "later");
        OutboxEvent earlier = newEvent(availableAt, "earlier");
        OutboxEvent future = newEvent(availableAt.plusSeconds(10), "future");
        outboxEventRepository.saveAll(List.of(later, earlier, future));
        outboxEventRepository.flush();

        List<OutboxEvent> pending = outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAscEventIdAsc(
                        OutboxEventStatus.PENDING,
                        availableAt.plusSeconds(2),
                        PageRequest.of(0, 10)
                );

        assertThat(pending).extracting(OutboxEvent::getPartitionKey)
                .containsExactly("earlier", "later");
    }

    private OutboxEvent newEvent(Instant createdAt, String partitionKey) throws Exception {
        return new OutboxEvent(
                UUID.randomUUID(),
                "FEED_CREATED",
                (short) 1,
                "FEED",
                UUID.randomUUID(),
                partitionKey,
                objectMapper.readTree("{\"partitionKey\":\"" + partitionKey + "\"}"),
                createdAt,
                createdAt
        );
    }
}
