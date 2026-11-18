package com.otboo.team03.directmessage.redis;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;

public record DirectMessageRedisMessage(
        String dmKey,
        DirectMessageDto directMessage
) {
}
