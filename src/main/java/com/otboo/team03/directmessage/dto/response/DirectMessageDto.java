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
        String content,
        DirectMessageSenderDto sender
) {

    public DirectMessageDto(
            UUID id,
            Instant createdAt,
            String dmKey,
            UUID senderId,
            UUID receiverId,
            String content
    ) {
        this(
                id,
                createdAt,
                dmKey,
                senderId,
                receiverId,
                content,
                new DirectMessageSenderDto(senderId)
        );
    }

    public static DirectMessageDto from(DirectMessage directMessage) {
        UUID senderId = directMessage.getSender().getId();
        return new DirectMessageDto(
                directMessage.getId(),
                directMessage.getCreatedAt(),
                directMessage.getDmKey(),
                senderId,
                directMessage.getReceiver().getId(),
                directMessage.getContent(),
                new DirectMessageSenderDto(senderId)
        );
    }
}
