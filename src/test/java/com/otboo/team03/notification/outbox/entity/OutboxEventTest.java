package com.otboo.team03.notification.outbox.entity;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

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
