
package com.otboo.team03.realtime.redis;

import com.otboo.team03.realtime.RealtimeConnectionTerminationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.Mockito.*;

class JwtRevocationRedisSubscriberTest {

    private final RealtimeConnectionTerminationService terminationService = mock(RealtimeConnectionTerminationService.class);

    private final JwtRevocationRedisSubscriber subscriber = new JwtRevocationRedisSubscriber(terminationService);

    @Nested
    @DisplayName("JWT 무효화 이벤트 수신")
    class OnMessageTest {

        @Test
        @DisplayName("유효한 사용자 ID를 수신하면 해당 사용자의 실시간 연결을 종료한다")
        void onMessage_validUserId_disconnectsRealtimeConnections() {
            // given
            UUID userId = UUID.randomUUID();
            Message message = mock(Message.class);
            when(message.getBody()).thenReturn(userId.toString().getBytes(StandardCharsets.UTF_8));

            // when
            subscriber.onMessage(message, null);

            // then
            verify(terminationService).disconnectAll(userId);
        }

        @Test
        @DisplayName("유효하지 않은 사용자 ID를 수신하면 실시간 연결을 종료하지 않는다")
        void onMessage_invalidUserId_doesNotDisconnect() {
            // given
            Message message = mock(Message.class);
            when(message.getBody()).thenReturn("invalid-user-id".getBytes(StandardCharsets.UTF_8));

            // when
            subscriber.onMessage(message, null);

            // then
            verifyNoInteractions(terminationService);
        }
    }
}
