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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
  @DisplayName("인증 정보가 없으면 HTTP 401 UNAUTHORIZED를 반환한다")
  void getProfile_unauthenticated() throws Exception {
    // given
    UUID userId = UUID.randomUUID();

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", userId)
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
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

}
