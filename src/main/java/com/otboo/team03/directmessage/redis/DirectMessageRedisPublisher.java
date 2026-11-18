package com.otboo.team03.directmessage.redis;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DirectMessageRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String directMessageChannel;

    public DirectMessageRedisPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${otboo.redis.direct-message-channel:otboo:direct-messages}") String directMessageChannel
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.directMessageChannel = directMessageChannel;
    }

    public void publish(DirectMessageDto directMessage) {
        try {
            String message = objectMapper.writeValueAsString(
                    new DirectMessageRedisMessage(directMessage.dmKey(), directMessage)
            );
            redisTemplate.convertAndSend(directMessageChannel, message);
        } catch (Exception exception) {
            throw new IllegalStateException("Redis DM 발행에 실패했습니다.", exception);
        }
    }
}
