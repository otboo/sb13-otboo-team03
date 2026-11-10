package com.otboo.team03.directmessage.websocket;

import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.global.security.OtbooUserDetailsService;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectMessageWebSocketInterceptorTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtRegistry jwtRegistry;

    @Mock
    private OtbooUserDetailsService userDetailsService;

    @Mock
    private DirectMessageDestinationValidator destinationValidator;

    @InjectMocks
    private DirectMessageWebSocketInterceptor interceptor;

    @Test
    void CONNECT_요청에서_액세스_토큰으로_사용자를_인증한다() {
        String accessToken = "access-token";
        UUID userId = UUID.randomUUID();
        OtbooUserDetails userDetails = userDetails(userId);
        Message<byte[]> message = connectMessage("Bearer " + accessToken);

        doNothing().when(jwtTokenProvider).validateAccessToken(accessToken);
        when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken)).thenReturn(true);
        when(jwtTokenProvider.getUserId(accessToken)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(userDetails);

        Message<?> result = interceptor.preSend(message, null);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isInstanceOf(Authentication.class);
        assertThat(((Authentication) accessor.getUser()).getPrincipal())
                .isEqualTo(userDetails);
    }

    @Test
    void CONNECT_요청에_인증_토큰이_없으면_거부한다() {
        Message<byte[]> message = connectMessage(null);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void STOMP_메시지_헤더가_없으면_메시지를_그대로_전달한다() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void 잘못된_인증_헤더_형식은_거부한다() {
        Message<byte[]> message = connectMessage("Basic access-token");

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 비활성화된_액세스_토큰은_거부한다() {
        String accessToken = "inactive-token";
        Message<byte[]> message = connectMessage("Bearer " + accessToken);
        doNothing().when(jwtTokenProvider).validateAccessToken(accessToken);
        when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void DM_참여자만_구독할_수_있다() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Authentication authentication = authentication(userId);
        String destination = "/sub/direct-messages_" + UUID.randomUUID();
        Message<byte[]> message = subscriptionMessage(destination, authentication);
        when(destinationValidator.isParticipant(userId, destination)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
    }

    @Test
    void DM_참여자가_아니면_구독할_수_없다() {
        UUID userId = UUID.randomUUID();
        Authentication authentication = authentication(userId);
        String destination = "/sub/direct-messages_" + UUID.randomUUID();
        when(destinationValidator.isParticipant(userId, destination)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(
                subscriptionMessage(destination, authentication),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 인증되지_않은_메시지는_거부한다() {
        String destination = "/sub/direct-messages_" + UUID.randomUUID();

        assertThatThrownBy(() -> interceptor.preSend(
                subscriptionMessage(destination, null),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 잘못된_송신_destination은_거부한다() {
        UUID userId = UUID.randomUUID();
        Authentication authentication = authentication(userId);
        Message<byte[]> message = sendMessage("/pub/invalid", authentication);

        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 허용된_송신_destination은_통과한다() {
        Authentication authentication = authentication(UUID.randomUUID());
        Message<byte[]> message = sendMessage("/pub/direct-messages_send", authentication);

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void 잘못된_인증_principal은_DM을_구독할_수_없다() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "wrong-principal",
                null,
                List.of()
        );
        String destination = "/sub/direct-messages_" + UUID.randomUUID();

        assertThatThrownBy(() -> interceptor.preSend(
                subscriptionMessage(destination, authentication),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscriptionMessage(
            String destination,
            Authentication authentication
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> sendMessage(
            String destination,
            Authentication authentication
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Authentication authentication(UUID userId) {
        OtbooUserDetails userDetails = userDetails(userId);
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    private OtbooUserDetails userDetails(UUID userId) {
        return new OtbooUserDetails(
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
    }
}
