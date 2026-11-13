package com.otboo.team03.notification.processed.repository;

import com.otboo.team03.global.config.JpaConfig;
import com.otboo.team03.global.config.QuerydslConfig;
import com.otboo.team03.notification.processed.entity.ProcessedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaConfig.class, QuerydslConfig.class})
@Testcontainers
class ProcessedEventRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void 처리_이벤트를_복합키로_저장하고_조회한다() {
        String consumerName = "notification-consumer";
        UUID eventId = UUID.randomUUID();
        Instant processedAt = Instant.parse("2026-11-13T00:00:00Z");

        ProcessedEvent saved = processedEventRepository.saveAndFlush(
                new ProcessedEvent(consumerName, eventId, processedAt)
        );

        assertThat(processedEventRepository.findById(
                new ProcessedEvent.Key(consumerName, eventId)
        )).get().satisfies(found -> {
            assertThat(found.getConsumerName()).isEqualTo(consumerName);
            assertThat(found.getEventId()).isEqualTo(eventId);
            assertThat(found.getProcessedAt()).isEqualTo(processedAt);
        });
        assertThat(saved.getEventId()).isEqualTo(eventId);
    }

    @Test
    void 같은_Consumer와_이벤트의_중복_기록은_한_번만_성공한다() {
        String consumerName = "notification-consumer";
        UUID eventId = UUID.randomUUID();
        Instant processedAt = Instant.parse("2026-11-13T00:00:00Z");

        int firstInsert = processedEventRepository.insertIfAbsent(
                consumerName, eventId, processedAt
        );
        int duplicateInsert = processedEventRepository.insertIfAbsent(
                consumerName, eventId, processedAt.plusSeconds(1)
        );

        assertThat(firstInsert).isEqualTo(1);
        assertThat(duplicateInsert).isZero();
        assertThat(processedEventRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_이벤트도_Consumer가_다르면_각각_처리할_수_있다() {
        UUID eventId = UUID.randomUUID();
        Instant processedAt = Instant.parse("2026-11-13T00:00:00Z");

        int notificationConsumerInsert = processedEventRepository.insertIfAbsent(
                "notification-consumer", eventId, processedAt
        );
        int auditConsumerInsert = processedEventRepository.insertIfAbsent(
                "audit-consumer", eventId, processedAt
        );

        assertThat(notificationConsumerInsert).isEqualTo(1);
        assertThat(auditConsumerInsert).isEqualTo(1);
        assertThat(processedEventRepository.count()).isEqualTo(2);
    }
}
