package com.otboo.team03.user.controller;

import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.exception.DuplicateEmailException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
}