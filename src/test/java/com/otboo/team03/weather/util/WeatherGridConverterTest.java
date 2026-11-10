package com.otboo.team03.weather.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class WeatherGridConverterTest {

  @ParameterizedTest(name = "{0} - 위도: {1}, 경도: {2} => X: {3}, Y: {4}")
  @CsvSource({
      "서울시청, 37.5665, 126.9780, 60, 127",
      "부산시청, 35.1795, 129.0756, 98, 76",
      "제주도청, 33.4890, 126.4983, 52, 38"
  })
  @DisplayName("위도와 경도가 주어지면 기상청 단기예보 격자 X, Y 좌표로 정확히 변환된다")
  void convertToGrid_success(String locationName, double lat, double lon, int expectedX, int expectedY) {

    // when
    WeatherGrid grid = WeatherGridConverter.toGrid(lat, lon);

    // then
    assertThat(grid.x()).isEqualTo(expectedX);
    assertThat(grid.y()).isEqualTo(expectedY);
  }
}