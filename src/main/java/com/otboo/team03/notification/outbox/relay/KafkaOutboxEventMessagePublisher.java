package com.otboo.team03.notification.outbox.relay;

import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutionException;

@Component
public class KafkaOutboxEventMessagePublisher implements OutboxEventMessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String notificationTopic;

    public KafkaOutboxEventMessagePublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${otboo.kafka.notification-topic}") String notificationTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.notificationTopic = notificationTopic;
    }

    @Override
    public void publish(OutboxEvent event) {
        String message = serialize(event);

        try {
            kafkaTemplate.send(
                    notificationTopic,
                    event.getPartitionKey(),
                    message
            ).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka 이벤트 발행이 중단되었습니다.", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Kafka 이벤트 발행에 실패했습니다.", exception.getCause());
        }
    }

    private String serialize(OutboxEvent event) {
        try {
            return objectMapper.writeValueAsString(new OutboxKafkaMessage(
                    event.getEventId(),
                    event.getEventType(),
                    event.getSchemaVersion(),
                    event.getOccurredAt(),
                    event.getAggregateId(),
                    event.getPayload()
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka 이벤트 payload 변환에 실패했습니다.", exception);
        }
    }
}
