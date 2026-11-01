package com.otboo.team03.profile;

import com.otboo.team03.profile.controller.ProfileController;
import com.otboo.team03.profile.dto.ProfileDto;
import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.profile.dto.Gender;
import com.otboo.team03.profile.exception.ProfileNotFoundException;
import com.otboo.team03.profile.service.ProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProfileController.class)
class ProfileControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ProfileService profileService;

  @Test
  @DisplayName("GET /api/users/{userId}/profiles 요청 시 HTTP 200과 ProfileDto를 반환한다")
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
  @DisplayName("존재하지 않는 userId로 프로필 조회 시 HTTP 404 NOT FOUND를 반환한다")
  void getProfile_notFound() throws Exception {
    // given
    UUID nonExistentUserId = UUID.randomUUID();
    given(profileService.getProfile(nonExistentUserId)).willThrow(new ProfileNotFoundException());

    // when & then
    mockMvc.perform(get("/api/users/{userId}/profiles", nonExistentUserId)
            .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isNotFound());
  }

}
