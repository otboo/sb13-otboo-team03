package com.otboo.team03.directmessage.redis;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.entity.DirectMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class DirectMessageRedisSubscriber implements MessageListener {

    private static final String SUBSCRIPTION_PREFIX = "/sub/direct-messages_";

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            DirectMessageRedisMessage redisMessage = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    DirectMessageRedisMessage.class
            );
            validate(redisMessage);
            DirectMessageDto directMessage = redisMessage.directMessage();
            messagingTemplate.convertAndSend(
                    SUBSCRIPTION_PREFIX + redisMessage.dmKey(),
                    directMessage
            );
        } catch (Exception exception) {
            log.warn("Redis DM 메시지 처리에 실패했습니다.", exception);
        }
    }

    private void validate(DirectMessageRedisMessage redisMessage) {
        if (redisMessage == null
                || redisMessage.dmKey() == null
                || redisMessage.directMessage() == null
                || !redisMessage.dmKey().equals(redisMessage.directMessage().dmKey())
                || !isValidDmKey(redisMessage.directMessage())) {
            throw new IllegalArgumentException("Redis DM 메시지의 필수 값이 누락되었거나 일치하지 않습니다.");
        }
    }

    private boolean isValidDmKey(DirectMessageDto directMessage) {
        if (directMessage.senderId() == null || directMessage.receiverId() == null) {
            return false;
        }
        return DirectMessage.createDmKey(
                directMessage.senderId(),
                directMessage.receiverId()
        ).equals(directMessage.dmKey());
    }
}
