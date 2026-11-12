package com.otboo.team03.notification.outbox.service;

import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.event.NotificationEventType;
import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventService outboxEventService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        outboxEventService = new OutboxEventService(outboxEventRepository, objectMapper);
    }

    @Test
    void 알림_이벤트를_Outbox_저장_형식으로_변환한다() {
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        NotificationEvent event = new NotificationEvent(
                eventId,
                NotificationEventType.USER_ROLE_CHANGED,
                NotificationEvent.CURRENT_SCHEMA_VERSION,
                Instant.parse("2026-11-12T00:00:00Z"),
                aggregateId,
                receiverId,
                UUID.randomUUID(),
                aggregateId,
                "권한 변경",
                "사용자 권한이 변경되었습니다.",
                NotificationLevel.INFO
        );

        outboxEventService.publish(event, "USER", receiverId.toString());

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getEventType()).isEqualTo("USER_ROLE_CHANGED");
        assertThat(saved.getSchemaVersion()).isEqualTo((short) 1);
        assertThat(saved.getAggregateType()).isEqualTo("USER");
        assertThat(saved.getAggregateId()).isEqualTo(aggregateId);
        assertThat(saved.getPartitionKey()).isEqualTo(receiverId.toString());
        assertThat(saved.getPayload().get("receiverId").asText()).isEqualTo(receiverId.toString());
        assertThat(saved.getStatus()).hasToString("PENDING");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void aggregateType이_비어_있으면_저장하지_않는다() {
        NotificationEvent event = event();

        assertThatThrownBy(() -> outboxEventService.publish(event, " ", "partition-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateType은 필수입니다.");

        verify(outboxEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void aggregateType이_null이면_저장하지_않는다() {
        NotificationEvent event = event();

        assertThatThrownBy(() -> outboxEventService.publish(event, null, "partition-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateType은 필수입니다.");

        verify(outboxEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void partitionKey가_비어_있으면_저장하지_않는다() {
        NotificationEvent event = event();

        assertThatThrownBy(() -> outboxEventService.publish(event, "USER", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partitionKey은 필수입니다.");

        verify(outboxEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void partitionKey가_null이면_저장하지_않는다() {
        NotificationEvent event = event();

        assertThatThrownBy(() -> outboxEventService.publish(event, "USER", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("partitionKey은 필수입니다.");

        verify(outboxEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void schemaVersion이_허용범위를_벗어나면_저장하지_않는다() {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID(),
                NotificationEventType.USER_ROLE_CHANGED,
                Short.MAX_VALUE + 1,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "권한 변경",
                "내용",
                NotificationLevel.INFO
        );

        assertThatThrownBy(() -> outboxEventService.publish(event, "USER", "partition-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("schemaVersion은 0 이상 32767 이하여야 합니다.");

        verify(outboxEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void schemaVersion이_음수이면_저장하지_않는다() {
        NotificationEvent event = new NotificationEvent(
                UUID.randomUUID(),
                NotificationEventType.USER_ROLE_CHANGED,
                -1,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "권한 변경",
                "내용",
                NotificationLevel.INFO
        );

        assertThatThrownBy(() -> outboxEventService.publish(event, "USER", "partition-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("schemaVersion은 0 이상 32767 이하여야 합니다.");

        verify(outboxEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void payload_변환에_실패하면_저장하지_않는다() throws Exception {
        ObjectMapper failingObjectMapper = mock(ObjectMapper.class);
        when(failingObjectMapper.writeValueAsString(any(NotificationEvent.class)))
                .thenThrow(new IllegalStateException("serialize failed"));
        OutboxEventService service = new OutboxEventService(outboxEventRepository, failingObjectMapper);

        assertThatThrownBy(() -> service.publish(event(), "USER", "partition-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("알림 이벤트 payload 변환에 실패했습니다.");

        verify(outboxEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private NotificationEvent event() {
        return new NotificationEvent(
                UUID.randomUUID(),
                NotificationEventType.USER_ROLE_CHANGED,
                NotificationEvent.CURRENT_SCHEMA_VERSION,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null,
                "권한 변경",
                "내용",
                NotificationLevel.INFO
        );
    }
}
