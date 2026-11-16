package com.otboo.team03.notification.outbox.relay;

import com.otboo.team03.notification.outbox.entity.OutboxEvent;
import com.otboo.team03.notification.outbox.entity.OutboxEventStatus;
import com.otboo.team03.notification.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAscCreatedAtAsc(
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
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private void validateBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize는 1 이상이어야 합니다.");
        }
    }
}
