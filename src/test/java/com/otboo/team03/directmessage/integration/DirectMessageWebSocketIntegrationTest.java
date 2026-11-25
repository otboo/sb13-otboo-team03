package com.otboo.team03.directmessage.integration;

import com.otboo.team03.directmessage.controller.DirectMessageWebSocketController;
import com.otboo.team03.directmessage.service.DirectMessageService;
import com.otboo.team03.directmessage.websocket.DirectMessageDestinationValidator;
import com.otboo.team03.directmessage.websocket.DirectMessageWebSocketInterceptor;
import com.otboo.team03.directmessage.dto.request.DirectMessageCreateRequest;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.global.security.OtbooUserDetailsService;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.realtime.websocket.WebSocketSessionRegistry;
import com.otboo.team03.realtime.websocket.WebSocketSessionTrackingHandlerDecoratorFactory;
import com.otboo.team03.realtime.RealtimeConnectionTerminationService;
import com.otboo.team03.notification.sse.SseConnectionManager;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.doNothing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = DirectMessageWebSocketIntegrationTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.endpoint.health.group.readiness.include=readinessState"
)
@ActiveProfiles("test")
class DirectMessageWebSocketIntegrationTest {

    private static final String ACCESS_TOKEN = "websocket-test-token";
    private static final UUID SENDER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID RECEIVER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000002"
    );

    @LocalServerPort
    private int port;

    @Autowired
    private DirectMessageService directMessageService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JwtRegistry jwtRegistry;

    @Autowired
    private OtbooUserDetailsService userDetailsService;

    @Autowired
    private RealtimeConnectionTerminationService terminationService;

    @BeforeEach
    void setUp() {
        clearInvocations(directMessageService);
        doNothing().when(jwtTokenProvider).validateAccessToken(ACCESS_TOKEN);
        when(jwtRegistry.hasActiveJwtInformationByAccessToken(ACCESS_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUserId(ACCESS_TOKEN)).thenReturn(SENDER_ID);
        when(userDetailsService.loadUserById(SENDER_ID)).thenReturn(userDetails(SENDER_ID));
    }

    @Test
    void 실제_STOMP_연결과_SEND를_통과한_인증_사용자의_DM을_서비스로_전달한다()
            throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(
                new StandardWebSocketClient()
        );
        client.setMessageConverter(new JacksonJsonMessageConverter());

        try {
            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer " + ACCESS_TOKEN);
            StompSession session = client.connectAsync(
                    "ws://localhost:" + port + "/ws/websocket",
                    new WebSocketHttpHeaders(),
                    connectHeaders,
                    new StompSessionHandlerAdapter() {}
            ).get(10, TimeUnit.SECONDS);

            session.send(
                    "/pub/direct-messages_send",
                    new DirectMessageCreateRequest(RECEIVER_ID, "메시지")
            );

            verify(directMessageService, timeout(5_000))
                    .send(SENDER_ID, RECEIVER_ID, "메시지");
            session.disconnect();
        } finally {
            try {
                terminationService.disconnectAll(SENDER_ID);
            } finally {
                client.stop();
            }
        }
    }

    @Test
    void SockJS_info_엔드포인트를_제공한다() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ws/info"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"websocket\":true");
    }

    private OtbooUserDetails userDetails(UUID userId) {
        return new OtbooUserDetails(
                new UserDto(
                        userId,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        "websocket@otboo.io",
                        "WebSocket 사용자",
                        UserRole.USER,
                        false
                ),
                "encoded-password"
        );
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
            "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
            "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
            "org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration",
            "org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration",
            "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
    })
    @Import({
            DirectMessageWebSocketController.class,
            DirectMessageWebSocketInterceptor.class,
            DirectMessageDestinationValidator.class,
            com.otboo.team03.directmessage.config.DirectMessageWebSocketConfig.class,
            WebSocketSessionRegistry.class,
            WebSocketSessionTrackingHandlerDecoratorFactory.class
    })
    static class Application {

        @Bean
        DirectMessageService directMessageService() {
            return mock(DirectMessageService.class);
        }

        @Bean
        RealtimeConnectionTerminationService realtimeConnectionTerminationService(
                WebSocketSessionRegistry sessionRegistry
        ) {
            return new RealtimeConnectionTerminationService(
                    new SseConnectionManager(),
                    sessionRegistry
            );
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return mock(JwtTokenProvider.class);
        }

        @Bean
        JwtRegistry jwtRegistry() {
            return mock(JwtRegistry.class);
        }

        @Bean
        OtbooUserDetailsService userDetailsService() {
            return mock(OtbooUserDetailsService.class);
        }

        @Bean
        SecurityFilterChain permitWebSocketHandshake(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}
