package com.otboo.team03.notification.controller;

import com.otboo.team03.global.config.SecurityConfig;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.global.security.OtbooUserDetailsService;
import com.otboo.team03.global.security.jwt.JwtAuthenticationFilter;
import com.otboo.team03.global.security.jwt.JwtLoginFailureHandler;
import com.otboo.team03.global.security.jwt.JwtLoginSuccessHandler;
import com.otboo.team03.global.security.jwt.JwtLogoutHandler;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.notification.service.NotificationService;
import com.otboo.team03.notification.sse.SseConnectionManager;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = SseControllerHttpTest.Application.class,
        properties = "management.endpoint.health.group.readiness.include=readinessState"
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SseControllerHttpTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SseConnectionManager sseConnectionManager;

    @Test
    void 인증되지_않은_SSE_요청은_401을_반환한다() throws Exception {
        clearInvocations(sseConnectionManager);

        mockMvc.perform(get("/api/sse"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(sseConnectionManager);
    }

    @Test
    void 인증된_SSE_요청은_실제_경로와_event_stream_응답을_사용한다() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(sseConnectionManager.connect(USER_ID)).thenReturn(emitter);

        var result = mockMvc.perform(get("/api/sse")
                        .header("Authorization", "Bearer test-token")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getContentType())
                .isEqualTo(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
    })
    @Import({SecurityConfig.class, SseController.class})
    static class Application {

        @Bean
        SseConnectionManager sseConnectionManager() {
            return mock(SseConnectionManager.class);
        }

        @Bean
        NotificationService notificationService() {
            return mock(NotificationService.class);
        }

        @Bean
        JwtLoginSuccessHandler successHandler() {
            return mock(JwtLoginSuccessHandler.class);
        }

        @Bean
        JwtLoginFailureHandler failureHandler() {
            return mock(JwtLoginFailureHandler.class);
        }

        @Bean
        JwtLogoutHandler logoutHandler() {
            return mock(JwtLogoutHandler.class);
        }

        @Bean
        JwtAuthenticationFilter jwtFilter() {
            JwtTokenProvider provider = mock(JwtTokenProvider.class);
            JwtRegistry registry = mock(JwtRegistry.class);
            OtbooUserDetailsService users = mock(OtbooUserDetailsService.class);
            when(provider.getUserId("test-token")).thenReturn(USER_ID);
            when(registry.hasActiveJwtInformationByAccessToken("test-token")).thenReturn(true);
            when(users.loadUserById(USER_ID)).thenReturn(new OtbooUserDetails(
                    new UserDto(USER_ID, Instant.now(), "user@otboo.io", "사용자", UserRole.USER, false),
                    "password"
            ));
            return new JwtAuthenticationFilter(provider, registry, users);
        }

        @Bean
        AuthenticationEntryPoint entryPoint() {
            return (request, response, exception) -> response.setStatus(401);
        }

        @Bean
        AccessDeniedHandler deniedHandler() {
            return (request, response, exception) -> response.setStatus(403);
        }
    }
}
