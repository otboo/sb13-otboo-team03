package com.otboo.team03.directmessage.controller;

import com.otboo.team03.directmessage.dto.response.DirectMessageDto;
import com.otboo.team03.directmessage.service.DirectMessageService;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DirectMessageControllerTest {

    @Mock
    private DirectMessageService directMessageService;

    @InjectMocks
    private DirectMessageController directMessageController;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(directMessageController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @Test
    void 인증된_사용자가_대화_메시지를_조회한다() throws Exception {
        UUID otherUserId = UUID.randomUUID();
        when(directMessageService.getMessages(
                eq(requesterId), eq(otherUserId), eq(null), eq(null), eq(20)
        )).thenReturn(new CursorResponse<>(
                List.of(), null, null, false, 0L, "createdAt", "DESCENDING"
        ));

        mockMvc.perform(get("/api/direct-messages")
                        .param("userId", otherUserId.toString())
                        .param("limit", "20")
                        .principal(authentication()))
                .andExpect(status().isOk());

        verify(directMessageService).getMessages(
                requesterId, otherUserId, null, null, 20
        );
    }

    @Test
    void 인증된_사용자가_DM을_전송한다() throws Exception {
        UUID receiverId = UUID.randomUUID();
        DirectMessageDto response = new DirectMessageDto(
                UUID.randomUUID(),
                Instant.parse("2026-01-01T00:00:00Z"),
                "dm-key",
                requesterId,
                receiverId,
                "안녕하세요."
        );
        when(directMessageService.send(requesterId, receiverId, "안녕하세요."))
                .thenReturn(response);

        mockMvc.perform(post("/api/direct-messages")
                        .contentType(APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + receiverId
                                + "\",\"content\":\"안녕하세요.\"}")
                        .principal(authentication()))
                .andExpect(status().isCreated());

        verify(directMessageService).send(requesterId, receiverId, "안녕하세요.");
    }

    @Test
    void 인증되지_않은_사용자는_DM을_전송할_수_없다() throws Exception {
        mockMvc.perform(post("/api/direct-messages")
                        .contentType(APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + UUID.randomUUID()
                                + "\",\"content\":\"메시지\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(directMessageService);
    }

    @Test
    void 인증되지_않은_사용자는_DM을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/direct-messages")
                        .param("userId", UUID.randomUUID().toString())
                        .param("limit", "20"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(directMessageService);
    }

    @Test
    void 인증되지_않은_Authentication은_거부한다() throws Exception {
        org.springframework.security.core.Authentication authentication = mock(
                org.springframework.security.core.Authentication.class
        );
        when(authentication.isAuthenticated()).thenReturn(false);

        mockMvc.perform(post("/api/direct-messages")
                        .contentType(APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + UUID.randomUUID()
                                + "\",\"content\":\"메시지\"}")
                        .principal(authentication))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(directMessageService);
    }

    @Test
    void 잘못된_인증_주체는_거부한다() throws Exception {
        org.springframework.security.core.Authentication authentication =
                new UsernamePasswordAuthenticationToken("wrong-principal", null, List.of());

        mockMvc.perform(post("/api/direct-messages")
                        .contentType(APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + UUID.randomUUID()
                                + "\",\"content\":\"메시지\"}")
                        .principal(authentication))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(directMessageService);
    }

    @Test
    void 수신자_ID가_없는_요청은_거부한다() throws Exception {
        mockMvc.perform(post("/api/direct-messages")
                        .contentType(APPLICATION_JSON)
                        .content("{\"content\":\"메시지\"}")
                        .principal(authentication()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(directMessageService);
    }

    @Test
    void 빈_메시지_요청은_거부한다() throws Exception {
        mockMvc.perform(post("/api/direct-messages")
                        .contentType(APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + UUID.randomUUID()
                                + "\",\"content\":\"   \"}")
                        .principal(authentication()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(directMessageService);
    }

    @Test
    void 천_자를_초과한_메시지_요청은_거부한다() throws Exception {
        mockMvc.perform(post("/api/direct-messages")
                        .contentType(APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + UUID.randomUUID()
                                + "\",\"content\":\"" + "a".repeat(1_001) + "\"}")
                        .principal(authentication()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(directMessageService);
    }

    private org.springframework.security.core.Authentication authentication() {
        UserDto userDto = new UserDto(
                requesterId,
                Instant.parse("2026-01-01T00:00:00Z"),
                "requester@otboo.io",
                "요청자",
                UserRole.USER,
                false
        );
        OtbooUserDetails userDetails = new OtbooUserDetails(userDto, "encoded-password");
        return new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );
    }
}
