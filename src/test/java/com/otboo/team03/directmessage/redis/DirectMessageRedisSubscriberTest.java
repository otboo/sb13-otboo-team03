package com.otboo.team03.directmessage.redis;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.entity.DirectMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectMessageRedisSubscriberTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Message message;

    @Test
    void 유효한_Redis_DM을_로컬_WebSocket으로_전달한다() throws Exception {
        DirectMessageRedisSubscriber subscriber = new DirectMessageRedisSubscriber(
                objectMapper,
                messagingTemplate
        );
        DirectMessageDto directMessage = directMessage();
        DirectMessageRedisMessage redisMessage = new DirectMessageRedisMessage(
                directMessage.dmKey(), directMessage
        );
        when(message.getBody()).thenReturn("redis-message".getBytes());
        when(objectMapper.readValue("redis-message", DirectMessageRedisMessage.class))
                .thenReturn(redisMessage);

        subscriber.onMessage(message, null);

        verify(messagingTemplate).convertAndSend(
                "/sub/direct-messages_" + directMessage.dmKey(),
                directMessage
        );
    }

    @Test
    void 잘못된_Redis_DM은_WebSocket으로_전달하지_않는다() throws Exception {
        DirectMessageRedisSubscriber subscriber = new DirectMessageRedisSubscriber(
                objectMapper,
                messagingTemplate
        );
        when(message.getBody()).thenReturn("invalid-message".getBytes());
        when(objectMapper.readValue("invalid-message", DirectMessageRedisMessage.class))
                .thenThrow(new IllegalArgumentException("invalid message"));

        subscriber.onMessage(message, null);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void DM_key가_본문과_다르면_전달하지_않는다() throws Exception {
        DirectMessageRedisSubscriber subscriber = new DirectMessageRedisSubscriber(
                objectMapper,
                messagingTemplate
        );
        DirectMessageDto directMessage = directMessage();
        DirectMessageRedisMessage redisMessage = new DirectMessageRedisMessage(
                UUID.randomUUID().toString(), directMessage
        );
        when(message.getBody()).thenReturn("redis-message".getBytes());
        when(objectMapper.readValue("redis-message", DirectMessageRedisMessage.class))
                .thenReturn(redisMessage);

        subscriber.onMessage(message, null);

        verifyNoInteractions(messagingTemplate);
    }

    private DirectMessageDto directMessage() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        return new DirectMessageDto(
                UUID.randomUUID(),
                Instant.parse("2026-11-18T00:00:00Z"),
                DirectMessage.createDmKey(senderId, receiverId),
                senderId,
                receiverId,
                "메시지"
        );
    }
}
