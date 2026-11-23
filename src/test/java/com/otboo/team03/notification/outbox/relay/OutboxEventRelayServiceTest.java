package com.otboo.team03.notification.outbox.relay;

import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.entity.OutboxEventStatus;
import com.otboo.team03.notification.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventRelayServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventMessagePublisher messagePublisher;

    private OutboxEventRelayService relayService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        relayService = new OutboxEventRelayService(
                outboxEventRepository,
                messagePublisher
        );
    }

    @Test
    void 대기중인_이벤트를_발행하고_PUBLISHED로_변경한다() throws Exception {
        OutboxEvent event = newEvent();
        when(outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAscEventIdAsc(
                        eq(OutboxEventStatus.PENDING),
                        any(Instant.class),
                        any(Pageable.class)
                )).thenReturn(List.of(event));

        int processedCount = relayService.relayPendingEvents(10);

        assertThat(processedCount).isOne();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        verify(messagePublisher).publish(event);
        verify(outboxEventRepository).save(event);
    }

    @Test
    void 배치_크기_기본값으로_대기중인_이벤트를_조회한다() {
        when(outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAscEventIdAsc(
                        eq(OutboxEventStatus.PENDING),
                        any(Instant.class),
                        any(Pageable.class)
                )).thenReturn(List.of());

        int processedCount = relayService.relayPendingEvents();

        assertThat(processedCount).isZero();
        verify(outboxEventRepository).findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAscEventIdAsc(
                eq(OutboxEventStatus.PENDING),
                any(Instant.class),
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.equals(PageRequest.of(0, OutboxEventRelayService.DEFAULT_BATCH_SIZE))
                )
        );
    }

    @Test
    void 발행에_실패하면_재시도할_수_있는_PENDING_상태로_변경한다() throws Exception {
        OutboxEvent event = newEvent();
        when(outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAscEventIdAsc(
                        eq(OutboxEventStatus.PENDING),
                        any(Instant.class),
                        any(Pageable.class)
                )).thenReturn(List.of(event));
        doThrow(new IllegalStateException("Kafka 연결 실패"))
                .when(messagePublisher).publish(event);

        relayService.relayPendingEvents(10);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isOne();
        assertThat(event.getLastError()).isEqualTo("Kafka 연결 실패");
        assertThat(event.getAvailableAt()).isAfter(event.getCreatedAt());
        verify(outboxEventRepository).save(event);
    }

    @Test
    void 발행_예외_메시지가_null이면_예외_클래스명을_저장한다() throws Exception {
        OutboxEvent event = newEvent();
        when(outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAscEventIdAsc(
                        eq(OutboxEventStatus.PENDING),
                        any(Instant.class),
                        any(Pageable.class)
                )).thenReturn(List.of(event));
        doThrow(new IllegalStateException())
                .when(messagePublisher).publish(event);

        relayService.relayPendingEvents(10);

        assertThat(event.getLastError()).isEqualTo(IllegalStateException.class.getSimpleName());
    }

    @Test
    void 한_이벤트_발행에_실패해도_다음_이벤트를_계속_처리한다() throws Exception {
        OutboxEvent failedEvent = newEvent();
        OutboxEvent successfulEvent = newEvent();
        when(outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAscEventIdAsc(
                        eq(OutboxEventStatus.PENDING),
                        any(Instant.class),
                        any(Pageable.class)
                )).thenReturn(List.of(failedEvent, successfulEvent));
        doThrow(new IllegalStateException("발행 실패"))
                .when(messagePublisher).publish(failedEvent);

        int processedCount = relayService.relayPendingEvents(10);

        assertThat(processedCount).isEqualTo(2);
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(successfulEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        verify(messagePublisher).publish(failedEvent);
        verify(messagePublisher).publish(successfulEvent);
        verify(outboxEventRepository).save(failedEvent);
        verify(outboxEventRepository).save(successfulEvent);
    }

    @Test
    void 최대_재시도_횟수를_초과하면_FAILED로_변경한다() throws Exception {
        OutboxEvent event = newEvent();
        Instant failedAt = Instant.now();
        event.recordFailure(
                failedAt,
                "첫 번째 실패",
                OutboxEventRelayService.MAX_ATTEMPTS,
                OutboxEventRelayService.RETRY_DELAY
        );
        event.recordFailure(
                failedAt,
                "두 번째 실패",
                OutboxEventRelayService.MAX_ATTEMPTS,
                OutboxEventRelayService.RETRY_DELAY
        );
        when(outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAscEventIdAsc(
                        eq(OutboxEventStatus.PENDING),
                        any(Instant.class),
                        any(Pageable.class)
                )).thenReturn(List.of(event));
        doThrow(new IllegalStateException("세 번째 실패"))
                .when(messagePublisher).publish(event);

        relayService.relayPendingEvents(10);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getAttemptCount())
                .isEqualTo(OutboxEventRelayService.MAX_ATTEMPTS);
        assertThat(event.getLastError()).isEqualTo("세 번째 실패");
        verify(outboxEventRepository).save(event);
    }

    @Test
    void 배치_크기가_0이하면_조회하지_않는다() {
        assertThatThrownBy(() -> relayService.relayPendingEvents(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batchSize는 1 이상이어야 합니다.");

        verifyNoInteractions(outboxEventRepository, messagePublisher);
    }

    private OutboxEvent newEvent() throws Exception {
        return new OutboxEvent(
                UUID.randomUUID(),
                "DIRECT_MESSAGE_RECEIVED",
                (short) 1,
                "DIRECT_MESSAGE",
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                objectMapper.readTree("{\"content\":\"test\"}"),
                Instant.now(),
                Instant.now()
        );
    }
}
