package com.otboo.team03.notification.outbox.entity;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 최대_시도_횟수가_0이면_재시도_정책을_거부한다() throws Exception {
        OutboxEvent event = newEvent();

        assertThatThrownBy(() -> event.recordFailure(
                Instant.now(),
                "발행 실패",
                0,
                Duration.ZERO
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재시도 정책이 올바르지 않습니다.");
    }

    @Test
    void 재시도_간격이_음수이면_재시도_정책을_거부한다() throws Exception {
        OutboxEvent event = newEvent();

        assertThatThrownBy(() -> event.recordFailure(
                Instant.now(),
                "발행 실패",
                3,
                Duration.ofSeconds(-1)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재시도 정책이 올바르지 않습니다.");
    }

    @Test
    void FAILED_이벤트를_재처리할_수_있도록_초기화한다() throws Exception {
        OutboxEvent event = newEvent();
        event.recordFailure(Instant.now(), "Kafka 실패", 1, Duration.ZERO);

        Instant requeuedAt = Instant.now().plusSeconds(1);
        event.requeue(requeuedAt);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttemptCount()).isZero();
        assertThat(event.getAvailableAt()).isEqualTo(requeuedAt);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getLastError()).isNull();
    }

    @Test
    void FAILED가_아닌_이벤트는_재처리할_수_없다() throws Exception {
        OutboxEvent event = newEvent();

        assertThatThrownBy(() -> event.requeue(Instant.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FAILED 상태의 이벤트만 재처리할 수 있습니다.");
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
