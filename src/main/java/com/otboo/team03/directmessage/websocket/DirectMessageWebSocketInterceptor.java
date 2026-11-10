package com.otboo.team03.directmessage.websocket;

import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.global.security.OtbooUserDetailsService;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DirectMessageWebSocketInterceptor implements ChannelInterceptor {

    private static final String SEND_DESTINATION = "/pub/direct-messages_send";

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRegistry jwtRegistry;
    private final OtbooUserDetailsService userDetailsService;
    private final DirectMessageDestinationValidator destinationValidator;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
            return message;
        }

        Authentication authentication = getAuthentication(accessor);
        if (authentication == null) {
            throw new IllegalArgumentException("WebSocket 인증이 필요합니다.");
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validateSubscription(authentication, accessor.getDestination());
        } else if (StompCommand.SEND.equals(accessor.getCommand())
                && !SEND_DESTINATION.equals(accessor.getDestination())) {
            throw new IllegalArgumentException("허용되지 않은 WebSocket 목적지입니다.");
        }

        return message;
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("WebSocket 인증 토큰이 필요합니다.");
        }

        String accessToken = authorization.substring("Bearer ".length());
        jwtTokenProvider.validateAccessToken(accessToken);
        if (!jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken)) {
            throw new IllegalArgumentException("활성화된 인증 토큰이 아닙니다.");
        }

        UUID userId = jwtTokenProvider.getUserId(accessToken);
        OtbooUserDetails userDetails = (OtbooUserDetails) userDetailsService.loadUserById(userId);
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }

    private void validateSubscription(Authentication authentication, String destination) {
        UUID userId = getAuthenticatedUserId(authentication);
        if (!destinationValidator.isParticipant(userId, destination)) {
            throw new IllegalArgumentException("DM 대화 참여자만 구독할 수 있습니다.");
        }
    }

    private Authentication getAuthentication(StompHeaderAccessor accessor) {
        return accessor.getUser() instanceof Authentication authentication
                ? authentication
                : null;
    }

    private UUID getAuthenticatedUserId(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof OtbooUserDetails userDetails)) {
            throw new IllegalArgumentException("유효한 WebSocket 사용자 정보가 없습니다.");
        }
        return userDetails.getUserDto().id();
    }
}
