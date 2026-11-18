package com.otboo.team03.notification.outbox.relay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(OutboxEventRelayService.class)
public class OutboxEventRelayScheduler {

    private final OutboxEventRelayService relayService;
    private final int batchSize;

    public OutboxEventRelayScheduler(
            OutboxEventRelayService relayService,
            @Value("${otboo.outbox.relay.batch-size:100}") int batchSize
    ) {
        this.relayService = relayService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${otboo.outbox.relay.fixed-delay:PT5S}")
    public void relayPendingEvents() {
        int processedCount = relayService.relayPendingEvents(batchSize);

        if (processedCount > 0) {
            log.debug("Outbox Relay processed events: count={}", processedCount);
        }
    }
}
