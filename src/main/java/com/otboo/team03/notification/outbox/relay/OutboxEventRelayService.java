package com.otboo.team03.notification.outbox.relay;

import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.entity.OutboxEventStatus;
import com.otboo.team03.notification.outbox.repository.OutboxEventRepository;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnBean(OutboxEventMessagePublisher.class)
@RequiredArgsConstructor
public class OutboxEventRelayService {

    static final int DEFAULT_BATCH_SIZE = 100;
    static final int MAX_ATTEMPTS = 3;
    static final Duration RETRY_DELAY = Duration.ofMinutes(1);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventMessagePublisher messagePublisher;

    @Transactional
    public int relayPendingEvents(int batchSize) {
        validateBatchSize(batchSize);

        Instant now = Instant.now();
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAscEventIdAsc(
                        OutboxEventStatus.PENDING,
                        now,
                        PageRequest.of(0, batchSize)
                );

        pendingEvents.forEach(event -> relay(event, now));
        return pendingEvents.size();
    }

    @Transactional
    public int relayPendingEvents() {
        return relayPendingEvents(DEFAULT_BATCH_SIZE);
    }

    @Transactional
    public void retryFailedEvent(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new OtbooException(ErrorCode.NOT_FOUND_OUTBOX_EVENT));
        if (event.getStatus() != OutboxEventStatus.FAILED) {
            throw new OtbooException(ErrorCode.OUTBOX_EVENT_NOT_FAILED);
        }

        event.requeue(Instant.now());
        outboxEventRepository.save(event);
    }

    private void relay(OutboxEvent event, Instant now) {
        try {
            messagePublisher.publish(event);
            event.markPublished(now);
        } catch (RuntimeException exception) {
            event.recordFailure(
                    now,
                    resolveErrorMessage(exception),
                    MAX_ATTEMPTS,
                    RETRY_DELAY
            );
        }

        outboxEventRepository.save(event);
    }

    private String resolveErrorMessage(RuntimeException exception) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }

        if (rootCause == exception || Objects.equals(message, rootCause.getMessage())) {
            return message;
        }

        String causeMessage = rootCause.getMessage() == null
                ? rootCause.getClass().getSimpleName()
                : rootCause.getMessage();
        return message + " (원인: " + causeMessage + ")";
    }

    private void validateBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 1 이상이어야 합니다.");
        }
    }
}
