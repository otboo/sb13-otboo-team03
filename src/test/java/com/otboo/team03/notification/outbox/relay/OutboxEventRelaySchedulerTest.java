package com.otboo.team03.notification.outbox.relay;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxEventRelaySchedulerTest {

    @Test
    void 설정된_배치_크기로_대기중인_이벤트를_처리한다() {
        OutboxEventRelayService relayService = mock(OutboxEventRelayService.class);
        when(relayService.relayPendingEvents(25)).thenReturn(2);
        OutboxEventRelayScheduler scheduler = new OutboxEventRelayScheduler(relayService, 25);

        scheduler.relayPendingEvents();

        verify(relayService).relayPendingEvents(25);
    }
}
