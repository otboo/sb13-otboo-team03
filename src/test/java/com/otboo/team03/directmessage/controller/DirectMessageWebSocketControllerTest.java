package com.otboo.team03.directmessage.controller;

import com.otboo.team03.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.service.DirectMessageService;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectMessageWebSocketControllerTest {

    @Mock
    private DirectMessageService directMessageService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private DirectMessageWebSocketController controller;

    @Test
    void 인증된_사용자_기준으로_DM을_전송하고_대화방에_전달한다() {
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        DirectMessageCreateRequest request = new DirectMessageCreateRequest(
                receiverId,
                "메시지"
        );
        DirectMessageDto response = new DirectMessageDto(
                UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"),
                "dm-key",
                senderId,
                receiverId,
                "메시지"
        );
        when(directMessageService.send(senderId, receiverId, "메시지"))
                .thenReturn(response);

        controller.send(request, authentication(senderId));

        verify(directMessageService).send(senderId, receiverId, "메시지");
        verify(messagingTemplate).convertAndSend(
                eq("/sub/direct-messages_" + com.otboo.team03.directmessage.entity.DirectMessage
                        .createDmKey(senderId, receiverId)),
                eq(response)
        );
    }

    @Test
    void 인증되지_않은_사용자는_WebSocket_DM을_전송할_수_없다() {
        DirectMessageCreateRequest request = new DirectMessageCreateRequest(
                UUID.randomUUID(),
                "메시지"
        );

        assertThatThrownBy(() -> controller.send(request, () -> "anonymous"))
                .isInstanceOf(OtbooException.class);
    }

    @Test
    void 인증_객체의_principal이_잘못되면_DM을_전송할_수_없다() {
        DirectMessageCreateRequest request = new DirectMessageCreateRequest(
                UUID.randomUUID(),
                "메시지"
        );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "wrong-principal",
                null
        );

        assertThatThrownBy(() -> controller.send(request, authentication))
                .isInstanceOf(OtbooException.class);
    }

    private Authentication authentication(UUID userId) {
        OtbooUserDetails userDetails = new OtbooUserDetails(
                new UserDto(
                        userId,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "user@otboo.io",
                        "사용자",
                        UserRole.USER,
                        false
                ),
                "encoded-password"
        );
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }
}
