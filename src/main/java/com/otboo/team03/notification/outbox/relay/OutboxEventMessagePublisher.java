package com.otboo.team03.notification.outbox.relay;

import com.otboo.team03.notification.outbox.entity.OutboxEvent;

@FunctionalInterface
public interface OutboxEventMessagePublisher {

    void publish(OutboxEvent event);
}
