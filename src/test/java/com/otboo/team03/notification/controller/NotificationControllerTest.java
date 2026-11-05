package com.otboo.team03.notification.controller;

import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.notification.dto.response.NotificationDto;
import com.otboo.team03.notification.entity.NotificationLevel;
import com.otboo.team03.notification.service.NotificationService;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private UUID receiverId;

    @BeforeEach
    void setUp() {
        receiverId = UUID.randomUUID();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void 인증된_사용자의_알림을_조회한다() throws Exception {
        when(notificationService.getNotifications(
                eq(receiverId),
                eq(null),
                eq(null),
                eq(20)
        )).thenReturn(new CursorResponse<>(
                List.of(new NotificationDto(
                        UUID.randomUUID(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        receiverId,
                        "알림",
                        "알림 내용",
                        NotificationLevel.INFO
                )),
                null,
                null,
                false,
                1L,
                "createdAt",
                "DESCENDING"
        ));

        mockMvc.perform(get("/api/notifications")
                        .param("limit", "20")
                        .principal(authentication()))
                .andExpect(status().isOk());

        verify(notificationService).getNotifications(receiverId, null, null, 20);
    }

    @Test
    void 커서와_ID를_전달해_다음_알림을_조회한다() throws Exception {
        Instant cursor = Instant.parse("2026-01-01T00:00:00Z");
        UUID idAfter = UUID.randomUUID();
        when(notificationService.getNotifications(
                eq(receiverId), eq(cursor), eq(idAfter), eq(10)
        )).thenReturn(new CursorResponse<>(
                List.of(), null, null, false, 0L, "createdAt", "DESCENDING"
        ));

        mockMvc.perform(get("/api/notifications")
                        .param("limit", "10")
                        .param("cursor", cursor.toString())
                        .param("idAfter", idAfter.toString())
                        .principal(authentication()))
                .andExpect(status().isOk());

        verify(notificationService).getNotifications(receiverId, cursor, idAfter, 10);
    }

    @Test
    void 알림을_읽음_처리한다() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
                        .principal(authentication()))
                .andExpect(status().isNoContent());

        verify(notificationService).markAsRead(eq(receiverId), eq(notificationId), any(Instant.class));
    }

    @Test
    void 인증되지_않은_사용자는_알림을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/notifications").param("limit", "20"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    private org.springframework.security.core.Authentication authentication() {
        UserDto userDto = new UserDto(
                receiverId,
                Instant.parse("2026-01-01T00:00:00Z"),
                "receiver@otboo.io",
                "수신자",
                UserRole.USER,
                false
        );
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new OtbooUserDetails(userDto, "encoded-password"),
                null,
                new OtbooUserDetails(userDto, "encoded-password").getAuthorities()
        );
    }
}
