package com.otboo.team03.weather.controller;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.dto.HourlyForecastDto;
import com.otboo.team03.weather.dto.WeatherResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
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
  @DisplayName("좌표(위도, 경도)로 날씨 조회 시 200 OK와 날씨 정보를 반환한다")
  void getWeather_success() throws Exception {
    // given: 직관적이면서도 재사용 가능한 헬퍼 호출
    Double lat = 37.4979;
    Double lon = 127.0276;

    HourlyForecastDto current = createHourlyForecast(20.0);
    WeatherResponse response = WeatherResponse.of(current, List.of(current));
    given(weatherService.getWeather(eq(lat), eq(lon))).willReturn(response);

    // when & then: 요청 및 검증
    mockMvc.perform(get("/api/weathers")
            .param("latitude", String.valueOf(lat))
            .param("longitude", String.valueOf(lon))
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentWeather.temperature").value(20.0));
  }
  private HourlyForecastDto createHourlyForecast(Double temperature) {
    return new HourlyForecastDto(
        UUID.randomUUID(),
        Instant.now(),
        temperature,
        SkyStatus.CLEAR,
        PrecipitationType.NONE,
        0.0,
        0.0,
        50.0,
        1.5
    );
  }



}
