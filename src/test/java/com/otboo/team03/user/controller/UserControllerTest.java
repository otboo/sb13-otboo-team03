package com.otboo.team03.user.controller;

import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.exception.DuplicateEmailException;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;


    @Nested
    @DisplayName("회원가입")
    class CreateUserTest {

        @Test
        @DisplayName("성공 - 201 Created")
        void createUser_success() throws Exception {
            // given
            UserCreateRequest request = new UserCreateRequest(
                    "홍길동",
                    "test@example.com",
                    "password123!"
            );

            UserDto response = new UserDto(
                    UUID.randomUUID(),
                    Instant.now(),
                    request.email(),
                    request.name(),
                    UserRole.USER,
                    false
            );

            given(userService.createUser(any(UserCreateRequest.class)))
                    .willReturn(response);

            MockMvc mockMvc = MockMvcBuilders
                    .standaloneSetup(userController)
                    .build();

            // when & then
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "홍길동",
                                      "email": "test@example.com",
                                      "password": "password123!"
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("test@example.com"))
                    .andExpect(jsonPath("$.name").value("홍길동"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.locked").value(false));
        }

        @Test
        @DisplayName("실패 - 유효하지 않은 이메일")
        void createUser_invalidEmail() throws Exception {
            // given
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            validator.afterPropertiesSet();

            MockMvc mockMvc = MockMvcBuilders
                    .standaloneSetup(userController)
                    .setValidator(validator)
                    .build();

            // when & then
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "홍길동",
                                      "email": "invalid-email",
                                      "password": "password123!"
                                    }
                                    """))
                    .andExpect(status().isBadRequest());

            validator.close();
        }

        @Test
        @DisplayName("실패 - 이메일 중복 (409 Conflict)")
        void createUser_duplicateEmail() throws Exception {
            // given
            given(userService.createUser(any(UserCreateRequest.class)))
                    .willThrow(new DuplicateEmailException());

            MockMvc mockMvc = MockMvcBuilders
                    .standaloneSetup(userController)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

            // when & then
            mockMvc.perform(post("/api/users")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                            {
                              "name": "홍길동",
                              "email": "test@example.com",
                              "password": "password123!"
                            }
                            """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.exceptionName").exists())
                    .andExpect(jsonPath("$.message").exists());
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePasswordTest {

        @Test
        @DisplayName("성공 - 새 비밀번호로 변경하면 204 No Content 반환")
        void changePassword_success_returns204() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            String newPassword = "newPassword123!";

            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(userController).build();

            // when & then
            mockMvc.perform(patch("/api/users/{userId}/password", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "password": "newPassword123!"
                                }
                                """))
                    .andExpect(status().isNoContent());

            verify(userService).changePassword(userId, newPassword);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자면 404 Not Found 반환")
        void changePassword_userNotFound_returns404() throws Exception {
            // given
            UUID userId = UUID.randomUUID();

            doThrow(new UserNotFoundException())
                    .when(userService).changePassword(eq(userId), any(String.class));

            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(userController)
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .build();

            // when & then
            mockMvc.perform(patch("/api/users/{userId}/password", userId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {
                                    "password": "newPassword123!"
                                }
                                """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.exceptionName").exists());

            verify(userService).changePassword(userId, "newPassword123!");
        }

        @Test
        @DisplayName("실패 - 빈 비밀번호 요청 시 400 Bad Request 반환")
        void changePassword_blankPassword_returns400() throws Exception {
            // given
            LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
            validator.afterPropertiesSet();

            try {
                MockMvc mockMvc = MockMvcBuilders.standaloneSetup(userController)
                        .setValidator(validator)
                        .build();

                // when & then
                mockMvc.perform(patch("/api/users/{userId}/password", UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "password": ""
                                    }
                                    """))
                        .andExpect(status().isBadRequest());

            } finally {
                validator.close();
            }
        }
    }

}