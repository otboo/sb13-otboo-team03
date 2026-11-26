package com.otboo.team03.weather.service;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.client.KakaoLocalClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

  @Mock
  private KakaoLocalClient kakaoLocalClient;

  @InjectMocks
  private LocationService locationService;

  @Test
  @DisplayName("위도와 경도가 주어지면 기상청 격자(x, y)와 행정구역명이 조합된 WeatherAPILocation을 반환한다")
  void getLocation_success() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;
    given(kakaoLocalClient.getRegionNames(latitude, longitude))
        .willReturn(List.of("서울특별시", "중구", "명동"));

    // when
    WeatherAPILocation result = locationService.getLocation(latitude, longitude);

    // then
    assertThat(result).isNotNull();
    assertThat(result.latitude()).isEqualTo(latitude);
    assertThat(result.longitude()).isEqualTo(longitude);
    // 서울시청 기상청 격자 검증
    assertThat(result.x()).isEqualTo(60);
    assertThat(result.y()).isEqualTo(127);
    // 카카오 행정동 명칭 검증
    assertThat(result.locationNames()).containsExactly("서울특별시", "중구", "명동");
  }

  @Test
  @DisplayName("위도 또는 경도가 null이면 null을 반환한다")
  void getLocation_nullCoordinates_returnsNull() {
    assertThat(locationService.getLocation(null, 126.9780)).isNull();
    assertThat(locationService.getLocation(37.5665, null)).isNull();
  }


}