package com.otboo.team03.notification.controller;

import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.notification.sse.SseConnectionManager;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseControllerTest {

    @Mock
    private SseConnectionManager sseConnectionManager;

    @InjectMocks
    private SseController sseController;

    @Test
    void 인증된_사용자의_SSE_연결을_생성한다() {
        UUID receiverId = UUID.randomUUID();
        Authentication authentication = authentication(receiverId);
        SseEmitter emitter = new SseEmitter();
        when(sseConnectionManager.connect(receiverId)).thenReturn(emitter);

        SseEmitter result = sseController.connect(authentication);

        assertThat(result).isSameAs(emitter);
        verify(sseConnectionManager).connect(receiverId);
    }

    @Test
    void 인증되지_않은_사용자는_SSE_연결을_생성할_수_없다() {
        assertThatThrownBy(() -> sseController.connect(null))
                .isInstanceOf(RuntimeException.class);
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
