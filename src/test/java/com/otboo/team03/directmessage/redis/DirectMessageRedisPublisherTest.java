package com.otboo.team03.directmessage.redis;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectMessageRedisPublisherTest {

    private static final String CHANNEL = "otboo:direct-messages";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void DM을_지정된_Redis_채널로_발행한다() throws Exception {
        DirectMessageRedisPublisher publisher = new DirectMessageRedisPublisher(
                redisTemplate,
                objectMapper,
                CHANNEL
        );
        DirectMessageDto directMessage = directMessage();
        when(objectMapper.writeValueAsString(new DirectMessageRedisMessage(
                directMessage.dmKey(), directMessage
        ))).thenReturn("redis-message");

        publisher.publish(directMessage);

        verify(redisTemplate).convertAndSend(CHANNEL, "redis-message");
    }

    @Test
    void 메시지_변환에_실패하면_예외를_던진다() throws Exception {
        DirectMessageRedisPublisher publisher = new DirectMessageRedisPublisher(
                redisTemplate,
                objectMapper,
                CHANNEL
        );
        DirectMessageDto directMessage = directMessage();
        when(objectMapper.writeValueAsString(new DirectMessageRedisMessage(
                directMessage.dmKey(), directMessage
        ))).thenThrow(new IllegalStateException("serialize failed"));

        assertThatThrownBy(() -> publisher.publish(directMessage))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Redis DM 발행에 실패했습니다.");
    }

    private DirectMessageDto directMessage() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        return new DirectMessageDto(
                UUID.randomUUID(),
                Instant.parse("2026-11-18T00:00:00Z"),
                com.otboo.team03.directmessage.entity.DirectMessage.createDmKey(senderId, receiverId),
                senderId,
                receiverId,
                "메시지"
        );
    }
}
