package com.otboo.team03.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.ForecastItem;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.Main;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.Rain;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.WeatherCondition;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.Wind;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OpenWeatherMapperTest {

  private final OpenWeatherMapper mapper = new OpenWeatherMapper();

  @Test
  @DisplayName("OpenWeatherMap 응답 데이터가 Weather 엔티티 목록으로 정상 변환된다")
  void toWeatherEntities_success() {
    // given
    long epochSeconds = 1731900000L;
    ForecastItem item = new ForecastItem(
        epochSeconds,
        new Main(18.5, 65.0),
        List.of(new WeatherCondition(800, "Clear", "clear sky")),
        new Wind(3.2),
        0.4, // pop: 40% 강수확률
        new Rain(5.0), // 3시간 5mm 강수량
        null
    );
    OpenWeatherForecastResponse response = new OpenWeatherForecastResponse("200", 0, 1, List.of(item), null);

    // when
    List<Weather> weathers = mapper.toWeatherEntities(response, 37.5665, 126.9780);

    // then
    assertThat(weathers).hasSize(1);
    Weather weather = weathers.get(0);
    assertThat(weather.getLatitude()).isEqualTo(37.5665);
    assertThat(weather.getLongitude()).isEqualTo(126.9780);
    assertThat(weather.getForecastAt()).isEqualTo(Instant.ofEpochSecond(epochSeconds));
    assertThat(weather.getTemperature()).isEqualTo(18.5);
    assertThat(weather.getHumidity()).isEqualTo(65.0);
    assertThat(weather.getWindSpeed()).isEqualTo(3.2);
    assertThat(weather.getPrecipitationProbability()).isEqualTo(40.0);
    assertThat(weather.getPrecipitationAmount()).isEqualTo(5.0);
    assertThat(weather.getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(weather.getPrecipitationType()).isEqualTo(PrecipitationType.NONE);
  }

  @ParameterizedTest
  @CsvSource({
      "800, CLEAR, NONE",             // 맑음
      "801, MOSTLY_CLOUDY, NONE",     // 구름 조금
      "804, CLOUDY, NONE",            // 흐림
      "500, CLOUDY, RAIN",            // 보통 비
      "511, CLOUDY, RAIN_SNOW",       // 어는 비
      "520, CLOUDY, SHOWER",          // 소나기
      "600, CLOUDY, SNOW",            // 눈
      "611, CLOUDY, RAIN_SNOW",       // 진눈깨비
      "701, CLOUDY, NONE"             // 안개
  })

  @DisplayName("Weather Condition Code에 따라 하늘상태와 강수형태가 정확히 추론된다")
  void resolveConditionCodes(int conditionCode, SkyStatus expectedSky, PrecipitationType expectedPrecip) {
    // given
    ForecastItem item = new ForecastItem(
        1731900000L,
        new Main(20.0, 50.0),
        List.of(new WeatherCondition(conditionCode, "Weather", "desc")),
        new Wind(1.0),
        0.0,
        null,
        null
    );
    OpenWeatherForecastResponse response = new OpenWeatherForecastResponse("200", 0, 1, List.of(item), null);

    // when
    List<Weather> weathers = mapper.toWeatherEntities(response, 37.5665, 126.9780);

    // then
    assertThat(weathers.get(0).getSkyStatus()).isEqualTo(expectedSky);
    assertThat(weathers.get(0).getPrecipitationType()).isEqualTo(expectedPrecip);
  }

  @Test
  @DisplayName("응답이 null이거나 예보 목록이 비어있으면 빈 리스트를 반환한다")
  void toWeatherEntities_whenEmptyOrNull_returnsEmptyList() {
    assertThat(mapper.toWeatherEntities(null, 37.5665, 126.9780)).isEmpty();
    assertThat(mapper.toWeatherEntities(new OpenWeatherForecastResponse("200", 0, 0, List.of(), null), 37.5665, 126.9780)).isEmpty();
  }


}