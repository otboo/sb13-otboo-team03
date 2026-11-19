package com.otboo.team03.notification.kafka;

import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.event.NotificationEvent;
import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.relay.KafkaOutboxEventMessagePublisher;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;

@Testcontainers
class KafkaNotificationIntegrationTest {

    private static final String TOPIC = "otboo.notification.events.test";

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUpTopic() throws Exception {
        objectMapper = new ObjectMapper();
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        ))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                    .all()
                    .get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void 실제_Kafka에서_Outbox_메시지를_발행하고_Consumer가_알림_이벤트를_처리한다()
            throws Exception {
        NotificationKafkaConsumerService consumerService = mock(NotificationKafkaConsumerService.class);
        NotificationKafkaConsumer consumer = new NotificationKafkaConsumer(
                objectMapper,
                consumerService
        );
        NotificationEvent event = event();
        OutboxEvent outboxEvent = outboxEvent(event);

        DefaultKafkaProducerFactory<String, String> producerFactory = producerFactory();
        try {
            KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactory);
            KafkaOutboxEventMessagePublisher publisher = new KafkaOutboxEventMessagePublisher(
                    kafkaTemplate,
                    objectMapper,
                    TOPIC
            );
            publisher.publish(outboxEvent);

            try (Consumer<String, String> kafkaConsumer = kafkaConsumer()) {
                kafkaConsumer.subscribe(List.of(TOPIC));
                ConsumerRecord<String, String> record = pollRecord(kafkaConsumer);

                consumer.consume(record.value());
            }
        } finally {
            producerFactory.destroy();
        }

        verify(consumerService).process(argThat(received ->
                received.eventId().equals(event.eventId())
                        && received.eventType() == event.eventType()
                        && received.schemaVersion() == event.schemaVersion()
                        && received.occurredAt().equals(event.occurredAt())
                        && received.aggregateId().equals(event.aggregateId())
                        && received.receiverId().equals(event.receiverId())
                        && received.actorId().equals(event.actorId())
                        && received.relatedId().equals(event.relatedId())
                        && received.title().equals(event.title())
                        && received.content().equals(event.content())
                        && received.level() == event.level()
        ));
        verifyNoMoreInteractions(consumerService);
    }

    private DefaultKafkaProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerProperties());
    }

    private Consumer<String, String> kafkaConsumer() {
        return new org.apache.kafka.clients.consumer.KafkaConsumer<>(consumerProperties());
    }

    private Map<String, Object> producerProperties() {
        return Map.of(
                org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers(),
                org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );
    }

    private Map<String, Object> consumerProperties() {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "notification-integration-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"
        );
    }

    private ConsumerRecord<String, String> pollRecord(Consumer<String, String> kafkaConsumer) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            ConsumerRecords<String, String> records = kafkaConsumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("Kafka 메시지를 제한 시간 안에 수신하지 못했습니다.");
    }

    private NotificationEvent event() {
        return new NotificationEvent(
                UUID.randomUUID(),
                com.otboo.team03.notification.event.NotificationEventType.DIRECT_MESSAGE_RECEIVED,
                NotificationEvent.CURRENT_SCHEMA_VERSION,
                Instant.parse("2026-11-19T00:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "새 메시지",
                "새로운 메시지가 도착했습니다.",
                NotificationLevel.INFO
        );
    }

    private OutboxEvent outboxEvent(NotificationEvent event) throws Exception {
        return new OutboxEvent(
                event.eventId(),
                event.eventType().name(),
                (short) event.schemaVersion(),
                "DIRECT_MESSAGE",
                event.aggregateId(),
                event.receiverId().toString(),
                objectMapper.readTree(objectMapper.writeValueAsString(event)),
                event.occurredAt(),
                Instant.parse("2026-11-19T00:00:01Z")
        );
    }
}
