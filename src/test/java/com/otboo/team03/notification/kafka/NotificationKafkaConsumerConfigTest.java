package com.otboo.team03.notification.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.FixedBackOff;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void 설정된_횟수만큼_재시도하고_이후_중단한다() {
        NotificationKafkaConsumerConfig config = new NotificationKafkaConsumerConfig();

        FixedBackOff backOff = config.createRetryBackOff(1_000L, 2L);
        BackOffExecution execution = backOff.start();

        assertThat(execution.nextBackOff()).isEqualTo(1_000L);
        assertThat(execution.nextBackOff()).isEqualTo(1_000L);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }

    @Test
    void 실패한_메시지를_DLT로_원본_파티션과_같은_파티션에_전송한다() {
        NotificationKafkaConsumerConfig config = new NotificationKafkaConsumerConfig();
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        DeadLetterPublishingRecoverer recoverer = config.createRecoverer(
                kafkaTemplate,
                "otboo.notification.events.DLT"
        );
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "otboo.notification.events",
                3,
                10L,
                "key",
                "message"
        );

        recoverer.accept(record, mock(Consumer.class), new IllegalStateException("failed"));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());

        ProducerRecord<String, String> dltRecord = captor.getValue();
        assertThat(dltRecord.topic()).isEqualTo("otboo.notification.events.DLT");
        assertThat(dltRecord.partition()).isEqualTo(3);
        assertThat(dltRecord.key()).isEqualTo("key");
    }
}
