package com.otboo.team03.profile.controller;

import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.profile.dto.Gender;
import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.profile.exception.ProfileNotFoundException;
import com.otboo.team03.profile.service.ProfileService;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

  @Mock
  private ProfileService profileService;

  @InjectMocks
  private ProfileController profileController;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(profileController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  private Authentication createAuth(UUID userId) {
    UserDto userDto = new UserDto(
        userId,
        Instant.now(),
        "test@example.com",
        "우디",
        UserRole.USER,
        false
    );
    OtbooUserDetails userDetails = new OtbooUserDetails(userDto, "password");
    return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
  }

  @Test
  @DisplayName("GET /api/users/{userId}/profiles 요청 시 본인 인증 성공하면 HTTP 200과 ProfileDto를 반환한다")
  void getProfile_success() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    WeatherAPILocation location = new WeatherAPILocation(37.5665, 126.9780, 60, 127, List.of("서울특별시", "중구"));
    ProfileDto responseDto = new ProfileDto(
        userId,
        "우디",
        Gender.MALE,
        LocalDate.of(1995, 5, 20),
        location,
        3,
        "https://cdn.otboo.io/profiles/woody.png"
    );

    given(profileService.getProfile(userId)).willReturn(responseDto);

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", userId)
            .principal(createAuth(userId))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.name").value("우디"))
        .andExpect(jsonPath("$.gender").value("MALE"))
        .andExpect(jsonPath("$.temperatureSensitivity").value(3))
        .andExpect(jsonPath("$.location.locationNames[0]").value("서울특별시"));
  }

  @Test
  @DisplayName("인증 정보가 없거나 유효하지 않으면 HTTP 401 UNAUTHORIZED를 반환한다")
  void getProfile_unauthenticated() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when & then: 1. authentication이 null인 경우
    mockMvc.perform(get("/api/users/{userId}/profiles", userId)
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isUnauthorized());

    // when & then: 2. isAuthenticated가 false인 경우
    Authentication unauthenticated = new UsernamePasswordAuthenticationToken("user", "password");
    mockMvc.perform(get("/api/users/{userId}/profiles", userId)
            .principal(unauthenticated)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    // when & then: 3. principal이 OtbooUserDetails가 아닌 경우
    Authentication wrongPrincipal = new UsernamePasswordAuthenticationToken("anonymousString", null, List.of());
    mockMvc.perform(get("/api/users/{userId}/profiles", userId)
            .principal(wrongPrincipal)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(profileService);
  }

  @Test
  @DisplayName("타인의 userId로 조회를 시도하면 HTTP 403 FORBIDDEN을 반환한다")
  void getProfile_forbidden_differentUser() throws Exception {
    // given
    UUID loggedInUserId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", otherUserId)
            .principal(createAuth(loggedInUserId))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isForbidden());

    verifyNoInteractions(profileService);
  }

  @Test
  @DisplayName("본인 인증 상태에서 존재하지 않는 userId로 프로필 조회 시 HTTP 404 NOT FOUND를 반환한다")
  void getProfile_notFound() throws Exception {
    // given
    UUID nonExistentUserId = UUID.randomUUID();
    given(profileService.getProfile(nonExistentUserId)).willThrow(new ProfileNotFoundException());

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", nonExistentUserId)
            .principal(createAuth(nonExistentUserId))
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH /api/users/{userId}/profiles 요청 시 본인 인증 성공하면 HTTP 200과 ProfileDto를 반환한다")
  void updateProfile_success() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    // 가짜 이미지 파일 준비
    MockMultipartFile image = new MockMultipartFile(
        "image", "avatar.png", "image/png", "fake-image".getBytes()
    );
    // 가짜 JSON 요청 준비 (JSON 문자열을 바이트로 변환)
    String jsonContent = """
        {
          "name": "우디",
          "gender": "FEMALE",
          "birthDate": "1998-01-01",
          "temperatureSensitivity": 4
        }
        """;
    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", "application/json", jsonContent.getBytes()
    );
    // 서비스가 반환해 줄 기대 DTO 준비
    ProfileDto responseDto = new ProfileDto(
        userId, "우디", Gender.FEMALE, LocalDate.of(1998, 1, 1),
        null, 4, "https://cdn.otboo.io/profiles/avatar.png"
    );
    // 서비스 Mock 동작 정의
    given(profileService.updateProfile(eq(userId), any(), any())).willReturn(responseDto);

    // when & then (멀티파트 PATCH 요청 전송 및 200 OK 검증)
    mockMvc.perform(multipart(HttpMethod.PATCH, "/api/users/{userId}/profiles", userId)
            .file(image)
            .file(requestPart)
            .principal(createAuth(userId)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.gender").value("FEMALE"));
  }

  @Test
  @DisplayName("타인의 userId로 수정을 시도하면 HTTP 403 FORBIDDEN을 반환하고 서비스를 호출하지 않는다")
  void updateProfile_forbidden_differentUser() throws Exception {
    // given (준비: 로그인한 유저 ID와 타인 유저 ID 생성)
    UUID loggedInUserId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    // when & then (타인 ID로 PATCH 멀티파트 요청 전송 및 403 검증)
    mockMvc.perform(multipart(HttpMethod.PATCH, "/api/users/{userId}/profiles", otherUserId)
            .principal(createAuth(loggedInUserId)))
        .andDo(print())
        .andExpect(status().isForbidden());
    // 핵심 보안 검증: 타인 요청이므로 서비스가 절대 호출되지 않았는지 확인
    verifyNoInteractions(profileService);
  }

  @Test
  @DisplayName("인증 정보 없이 프로필 수정을 시도하면 HTTP 401 UNAUTHORIZED를 반환하고 서비스를 호출하지 않는다")
  void updateProfile_unauthenticated() throws Exception {
    // given
    UUID userId = UUID.randomUUID();
    // when & then (인증 principal 없이 요청 전송 시 401 검증)
    mockMvc.perform(multipart(HttpMethod.PATCH, "/api/users/{userId}/profiles", userId))
        .andDo(print())
        .andExpect(status().isUnauthorized());
    // 서비스가 호출되지 않았는지 검증
    verifyNoInteractions(profileService);
  }

}
