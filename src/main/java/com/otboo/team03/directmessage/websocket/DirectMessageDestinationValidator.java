package com.otboo.team03.directmessage.websocket;

import com.otboo.team03.directmessage.entity.DirectMessage;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DirectMessageDestinationValidator {

    private static final String SUBSCRIPTION_PREFIX = "/sub/direct-messages_";

    public boolean isParticipant(UUID userId, String destination) {
        if (userId == null || destination == null
                || !destination.startsWith(SUBSCRIPTION_PREFIX)) {
            return false;
        }

        String dmKey = destination.substring(SUBSCRIPTION_PREFIX.length());
        String[] participantIds = dmKey.split("_", -1);
        if (participantIds.length != 2) {
            return false;
        }

        try {
            UUID firstUserId = UUID.fromString(participantIds[0]);
            UUID secondUserId = UUID.fromString(participantIds[1]);
            String normalizedDmKey = DirectMessage.createDmKey(firstUserId, secondUserId);

            return normalizedDmKey.equals(dmKey)
                    && (userId.equals(firstUserId) || userId.equals(secondUserId));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
