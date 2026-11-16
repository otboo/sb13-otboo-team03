package com.otboo.team03.weather.controller;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.dto.HumidityDto;
import com.otboo.team03.weather.dto.PrecipitationDto;
import com.otboo.team03.weather.dto.TemperatureDto;
import com.otboo.team03.weather.dto.WeatherDto;
import com.otboo.team03.weather.dto.WindSpeedDto;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.service.LocationService;
import com.otboo.team03.weather.service.WeatherService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WeatherControllerTest {

  private MockMvc mockMvc;

  @Mock
  private LocationService locationService;

  @Mock
  private WeatherService weatherService;

  @InjectMocks
  private WeatherController weatherController;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(weatherController).build();
  }

  @Test
  @DisplayName("위도와 경도로 위치 정보를 요청하면 200 OK와 WeatherAPILocation을 반환한다")
  void getLocation_success() throws Exception {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    WeatherAPILocation location = new WeatherAPILocation(
        latitude, longitude, 60, 127, List.of("서울특별시", "중구", "명동")
    );
    given(locationService.getLocation(latitude, longitude)).willReturn(location);

    // when & then
    mockMvc.perform(get("/api/weathers/location")
            .param("latitude", String.valueOf(latitude))
            .param("longitude", String.valueOf(longitude)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latitude").value(latitude))
        .andExpect(jsonPath("$.longitude").value(longitude))
        .andExpect(jsonPath("$.x").value(60))
        .andExpect(jsonPath("$.y").value(127))
        .andExpect(jsonPath("$.locationNames[0]").value("서울특별시"))
        .andExpect(jsonPath("$.locationNames[1]").value("중구"))
        .andExpect(jsonPath("$.locationNames[2]").value("명동"));
  }

  @Test
  @DisplayName("좌표 기반 날씨 조회 성공 시 Swagger 규격(배열)에 맞춰 응답한다")
  void getWeather_success() throws Exception {
    // given
    WeatherDto mockWeather = new WeatherDto(
        UUID.randomUUID(),
        Instant.now(),
        Instant.now(),
        new WeatherAPILocation(37.4979, 127.0276, 61, 125, List.of("서울특별시", "강남구")),
        SkyStatus.CLEAR,
        new PrecipitationDto(PrecipitationType.NONE, 0.0, 0.0),
        new HumidityDto(60.0, 0.0),
        new TemperatureDto(18.5, 0.0, 18.5, 18.5),
        new WindSpeedDto(2.5, "WEAK")
    );
    given(weatherService.getWeathers(anyDouble(), anyDouble()))
        .willReturn(List.of(mockWeather));
    // 2. When & Then: JSONPath 검증 수정
    mockMvc.perform(get("/api/weathers")
            .param("longitude", "127.0276")
            .param("latitude", "37.4979"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())                           // 1. 최상위가 배열([])인가
        .andExpect(jsonPath("$[0].id").exists())                     // 2. weatherId가 아닌 id인가
        .andExpect(jsonPath("$[0].temperature.current").value(18.5)) // 3. 중첩 객체(temperature.current)인가
        .andExpect(jsonPath("$[0].skyStatus").value("CLEAR"))
        .andExpect(jsonPath("$[0].humidity.current").value(60.0))
        .andExpect(jsonPath("$[0].windSpeed.asWord").value("WEAK"));
  }



}
