package com.otboo.team03.directmessage.controller;

import com.otboo.team03.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.entity.DirectMessage;
import com.otboo.team03.directmessage.service.DirectMessageService;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

    private static final String SUBSCRIPTION_PREFIX = "/sub/direct-messages_";

    private final DirectMessageService directMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/direct-messages_send")
    public void send(DirectMessageCreateRequest request, Principal principal) {
        UUID senderId = getAuthenticatedUserId(principal);
        DirectMessageDto response = directMessageService.send(
                senderId,
                request.receiverId(),
                request.content()
        );
        String dmKey = DirectMessage.createDmKey(senderId, request.receiverId());

        messagingTemplate.convertAndSend(SUBSCRIPTION_PREFIX + dmKey, response);
    }

    private UUID getAuthenticatedUserId(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof OtbooUserDetails userDetails)) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return userDetails.getUserDto().id();
    }
}
