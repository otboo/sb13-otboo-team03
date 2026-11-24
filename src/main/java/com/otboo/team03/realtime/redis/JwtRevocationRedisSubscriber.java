package com.otboo.team03.realtime.redis;

import com.otboo.team03.realtime.RealtimeConnectionTerminationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtRevocationRedisSubscriber implements MessageListener {

    private final RealtimeConnectionTerminationService terminationService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            UUID userId = UUID.fromString(payload);

            if (!userId.toString().equalsIgnoreCase(payload)) {
                throw new IllegalArgumentException("잘못된 사용자 ID 형식입니다.");
            }

            terminationService.disconnectAll(userId);
        } catch (RuntimeException exception) {
            log.warn("JWT 무효화 이벤트 처리에 실패했습니다.", exception);
        }
    }
}
