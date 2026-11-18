package com.otboo.team03.notification.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationKafkaConsumerConfigTest {

    @Test
    void Kafka_오류_핸들러를_생성한다() {
        NotificationKafkaConsumerConfig config = new NotificationKafkaConsumerConfig();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

        DefaultErrorHandler errorHandler = config.notificationKafkaErrorHandler(
                kafkaTemplate,
                "otboo.notification.events.DLT",
                1_000L,
                2L
        );

        assertThat(errorHandler).isNotNull();
    }

    @Test
    void 원본_파티션과_같은_DLT_파티션을_선택한다() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "otboo.notification.events",
                3,
                10L,
                "key",
                "message"
        );

        TopicPartition dltPartition = NotificationKafkaConsumerConfig.resolveDltPartition(
                record,
                "otboo.notification.events.DLT"
        );

        assertThat(dltPartition).isEqualTo(
                new TopicPartition("otboo.notification.events.DLT", 3)
        );
    }
}
