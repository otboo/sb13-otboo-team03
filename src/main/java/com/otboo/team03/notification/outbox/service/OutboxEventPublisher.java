package com.otboo.team03.notification.outbox.service;

import com.otboo.team03.notification.event.NotificationEvent;

public interface OutboxEventPublisher {

    void publish(NotificationEvent event, String aggregateType, String partitionKey);
}
