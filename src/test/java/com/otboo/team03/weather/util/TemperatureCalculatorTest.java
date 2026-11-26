package com.otboo.team03.weather.util;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TemperatureCalculatorTest {

  private final TemperatureCalculator calculator = new TemperatureCalculator();

  @Test
  @DisplayName("1. 기온 목록에서 당일 최저 기온과 최고 기온을 정확하게 계산한다")
  void calculateDailyMinMax() {
    // given: 하루 동안의 기온 예보 목록
    List<Double> temperatures = List.of(18.0, 20.0, 24.5, 16.0, 22.0);

    // when: 계산기 호출
    Double minTemp = calculator.calculateMin(temperatures);
    Double maxTemp = calculator.calculateMax(temperatures);

    // then: 최저 16.0도, 최고 24.5도가 맞는지 검증
    assertThat(minTemp).isEqualTo(16.0);
    assertThat(maxTemp).isEqualTo(24.5);
  }

  @Test
  @DisplayName("2. 현재 기온과 어제 기온의 차이를 정확하게 계산한다 (소수점 1자리)")
  void calculateDifferenceFromYesterday() {
    // given
    Double currentTemp = 21.0;
    Double yesterdayTemp = 18.5;

    // when: 어제 대비 기온 차이 계산 (현재 - 어제)
    Double diff = calculator.calculateDifference(currentTemp, yesterdayTemp);

    // then: 21.0 - 18.5 = 2.5도 차이 검증
    assertThat(diff).isEqualTo(2.5);
  }

  @Test
  @DisplayName("3. 어제 기온 데이터가 없는 경우 null을 안전하게 반환한다")
  void calculateDifference_whenYesterdayNull_returnsNull() {
    // given
    Double currentTemp = 20.0;
    Double yesterdayTemp = null; // 어제 데이터 없음

    // when
    Double diff = calculator.calculateDifference(currentTemp, yesterdayTemp);

    // then
    assertThat(diff).isNull();
  }

  @Test
  @DisplayName("4. 기온 목록이 null이거나 비어있으면 null을 반환한다")
  void calculateMinMax_whenEmptyOrNull_returnsNull() {
    assertThat(calculator.calculateMin(null)).isNull();
    assertThat(calculator.calculateMin(List.of())).isNull();
    assertThat(calculator.calculateMax(null)).isNull();
    assertThat(calculator.calculateMax(List.of())).isNull();
  }
  @Test
  @DisplayName("5. 현재 기온이 null인 경우에도 null을 안전하게 반환한다")
  void calculateDifference_whenCurrentNull_returnsNull() {
    assertThat(calculator.calculateDifference(null, 20.0)).isNull();
  }


}