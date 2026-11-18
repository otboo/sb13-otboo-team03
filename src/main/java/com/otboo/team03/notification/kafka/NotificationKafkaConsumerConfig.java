package com.otboo.team03.notification.kafka;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class NotificationKafkaConsumerConfig {

    @Bean
    DefaultErrorHandler notificationKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${otboo.kafka.notification-dlt-topic}") String dltTopic,
            @Value("${otboo.kafka.consumer.retry-backoff-ms:1000}") long retryBackoffMs,
            @Value("${otboo.kafka.consumer.retry-max-attempts:2}") long retryMaxAttempts
    ) {
        DeadLetterPublishingRecoverer recoverer = createRecoverer(kafkaTemplate, dltTopic);

        return new DefaultErrorHandler(
                recoverer,
                createRetryBackOff(retryBackoffMs, retryMaxAttempts)
        );
    }

    DeadLetterPublishingRecoverer createRecoverer(
            KafkaTemplate<String, String> kafkaTemplate,
            String dltTopic
    ) {
        return new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ignored) -> resolveDltPartition(record, dltTopic)
        );
    }

    FixedBackOff createRetryBackOff(long retryBackoffMs, long retryMaxAttempts) {
        return new FixedBackOff(retryBackoffMs, retryMaxAttempts);
    }

    static TopicPartition resolveDltPartition(
            ConsumerRecord<?, ?> record,
            String dltTopic
    ) {
        return new TopicPartition(dltTopic, record.partition());
    }
}
