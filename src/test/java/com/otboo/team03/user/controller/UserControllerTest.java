package com.otboo.team03.user.controller;

import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.user.dto.request.UserCreateRequest;
import com.otboo.team03.user.dto.request.UserListRequest;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import com.otboo.team03.user.exception.DuplicateEmailException;
import com.otboo.team03.user.service.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

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
        @DisplayName("실패 - 이메일 중복 (400 Bad Request)")
        void createUser_duplicateEmail() throws Exception {
            // given
            given(userService.createUser(any(UserCreateRequest.class)))
                    .willThrow(new DuplicateEmailException());

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
    @DisplayName("사용자 목록 조회")
    class GetUserListTest {

        @Test
        @DisplayName("성공 - 사용자 목록 조회 시 200 OK")
        void getUserList_success() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

            UserDto userDto = new UserDto(userId, createdAt, "alice@example.com", "Alice", UserRole.USER, false);
            CursorResponse<UserDto> response = new CursorResponse<>(
                    List.of(userDto), null, null, false, 1L, "email", "ASCENDING"
            );

            given(userService.getUsers(any(UserListRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/users")
                            .param("limit", "10")
                            .param("sortBy", "email")
                            .param("sortDirection", "ASCENDING"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].email").value("alice@example.com"))
                    .andExpect(jsonPath("$.data[0].role").value("USER"))
                    .andExpect(jsonPath("$.hasNext").value(false))
                    .andExpect(jsonPath("$.totalCount").value(1))
                    .andExpect(jsonPath("$.sortBy").value("email"))
                    .andExpect(jsonPath("$.sortDirection").value("ASCENDING"));
        }

        @Test
        @DisplayName("실패 - 필수 파라미터 누락 시 400 Bad Request")
        void getUserList_missingRequiredParameters() throws Exception {
            // when & then
            mockMvc.perform(get("/api/users"))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).getUsers(any());
        }

        @Test
        @DisplayName("실패 - 유효하지 않은 정렬 기준 입력 시 400 Bad Request")
        void getUserList_invalidSortBy() throws Exception {
            // when & then
            mockMvc.perform(get("/api/users")
                            .param("limit", "10")
                            .param("sortBy", "name")
                            .param("sortDirection", "ASCENDING"))
                    .andExpect(status().isBadRequest());

            verify(userService, never()).getUsers(any());
        }

        @Test
        @DisplayName("성공 - 조회 조건을 Service에 정확하게 전달")
        void getUserList_passQueryParameters() throws Exception {
            // given
            UUID idAfter = UUID.randomUUID();

            CursorResponse<UserDto> response = new CursorResponse<>(
                    List.of(), null, null, false, 0L, "email", "DESCENDING"
            );

            given(userService.getUsers(any(UserListRequest.class))).willReturn(response);

            // when
            mockMvc.perform(get("/api/users")
                            .param("cursor", "bob@example.com")
                            .param("idAfter", idAfter.toString())
                            .param("limit", "5")
                            .param("sortBy", "email")
                            .param("sortDirection", "DESCENDING")
                            .param("emailLike", "example.com")
                            .param("roleEqual", "USER")
                            .param("locked", "false"))
                    .andExpect(status().isOk());

            // then
            ArgumentCaptor<UserListRequest> captor = ArgumentCaptor.forClass(UserListRequest.class);
            verify(userService).getUsers(captor.capture());

            UserListRequest request = captor.getValue();

            assertThat(request.cursor()).isEqualTo("bob@example.com");
            assertThat(request.idAfter()).isEqualTo(idAfter);
            assertThat(request.limit()).isEqualTo(5);
            assertThat(request.sortBy()).isEqualTo("email");
            assertThat(request.sortDirection()).isEqualTo("DESCENDING");
            assertThat(request.emailLike()).isEqualTo("example.com");
            assertThat(request.roleEqual()).isEqualTo(UserRole.USER);
            assertThat(request.locked()).isFalse();
        }
    }
}