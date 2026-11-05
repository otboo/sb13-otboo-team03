package com.otboo.team03.directmessage.dto.response;

import com.otboo.team03.directmessage.entity.DirectMessage;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageDto(
        UUID id,
        Instant createdAt,
        String dmKey,
        UUID senderId,
        UUID receiverId,
        String content
) {

    public static DirectMessageDto from(DirectMessage directMessage) {
        return new DirectMessageDto(
                directMessage.getId(),
                directMessage.getCreatedAt(),
                directMessage.getDmKey(),
                directMessage.getSender().getId(),
                directMessage.getReceiver().getId(),
                directMessage.getContent()
        );
    }
}
