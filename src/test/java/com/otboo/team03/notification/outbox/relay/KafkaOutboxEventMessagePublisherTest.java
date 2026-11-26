package com.otboo.team03.notification.outbox.relay;

import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaOutboxEventMessagePublisherTest {

    private static final String TOPIC = "otboo.notification.events";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaOutboxEventMessagePublisher publisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        publisher = new KafkaOutboxEventMessagePublisher(
                kafkaTemplate,
                objectMapper,
                TOPIC
        );
    }

    @Test
    void Outbox_이벤트를_파티션키와_JSON_메시지로_발행한다() throws Exception {
        OutboxEvent event = newEvent();
        when(kafkaTemplate.send(eq(TOPIC), eq(event.getPartitionKey()), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(event);

        verify(kafkaTemplate).send(
                eq(TOPIC),
                eq(event.getPartitionKey()),
                org.mockito.ArgumentMatchers.argThat(message ->
                        message.contains(event.getEventId().toString())
                                && message.contains(event.getEventType())
                                && message.contains(event.getOccurredAt().toString())
                                && message.contains("content")
                )
        );
    }

    @Test
    void Kafka_발행이_실패하면_예외를_전파한다() throws Exception {
        OutboxEvent event = newEvent();
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafkaTemplate.send(eq(TOPIC), eq(event.getPartitionKey()), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(failedFuture);

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 이벤트 발행에 실패했습니다.");
    }

    @Test
    void Kafka_발행_중단_시_인터럽트_상태를_복구하고_예외를_전파한다() throws Exception {
        OutboxEvent event = newEvent();
        CompletableFuture<SendResult<String, String>> interruptedFuture =
                org.mockito.Mockito.mock(CompletableFuture.class);
        when(interruptedFuture.get(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(java.util.concurrent.TimeUnit.class)
        )).thenThrow(new InterruptedException());
        when(kafkaTemplate.send(eq(TOPIC), eq(event.getPartitionKey()), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(interruptedFuture);

        assertThatThrownBy(() -> publisher.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 이벤트 발행이 중단되었습니다.");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();
    }

    @Test
    void Kafka_발행이_제한시간을_초과하면_예외를_전파한다() throws Exception {
        OutboxEvent event = newEvent();
        CompletableFuture<SendResult<String, String>> pendingFuture =
                org.mockito.Mockito.mock(CompletableFuture.class);
        when(pendingFuture.get(eq(10L), eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenThrow(new java.util.concurrent.TimeoutException());
        when(kafkaTemplate.send(eq(TOPIC), eq(event.getPartitionKey()), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(pendingFuture);
        KafkaOutboxEventMessagePublisher timeoutPublisher = new KafkaOutboxEventMessagePublisher(
                kafkaTemplate,
                objectMapper,
                TOPIC,
                Duration.ofMillis(10)
        );

        assertThatThrownBy(() -> timeoutPublisher.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 이벤트 발행 시간이 초과되었습니다.");
    }

    @Test
    void Kafka_메시지_변환에_실패하면_예외를_전파한다() throws Exception {
        ObjectMapper failingObjectMapper = org.mockito.Mockito.mock(ObjectMapper.class);
        when(failingObjectMapper.writeValueAsString(any(OutboxKafkaMessage.class)))
                .thenThrow(new IllegalStateException("serialize failed"));
        KafkaOutboxEventMessagePublisher failingPublisher = new KafkaOutboxEventMessagePublisher(
                kafkaTemplate,
                failingObjectMapper,
                TOPIC
        );

        assertThatThrownBy(() -> failingPublisher.publish(newEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Kafka 이벤트 payload 변환에 실패했습니다.");

        verifyNoInteractions(kafkaTemplate);
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
                Instant.parse("2026-11-12T00:00:00Z"),
                Instant.now()
        );
    }
}
