package com.otboo.team03.weather.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.fixture.WeatherFixture;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WeatherRepositoryTest {

  @Autowired
  private WeatherRepository weatherRepository;

  @Test
  @DisplayName("날씨 엔티티 저장 및 ID 단건 조회가 정상 동작한다.")
  void saveAndFindById() {
    // given
    Weather weather = WeatherFixture.createDefault();

    // when
    Weather saved = weatherRepository.save(weather);
    Optional<Weather> found = weatherRepository.findById(saved.getId());

    // then
    assertThat(found).isPresent();
    assertThat(found.get().getTemperature()).isEqualTo(22.0);
    assertThat(found.get().getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(found.get().getPrecipitationType()).isEqualTo(PrecipitationType.NONE);
    assertThat(found.get().getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("좌표 기준 최근 예보 단건 조회가 정상 동작한다.")
  void findTopByLatitudeAndLongitudeOrderByForecastAtDesc() {
    // given
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Weather w1 = Weather.builder()
        .latitude(WeatherFixture.DEFAULT_LAT)
        .longitude(WeatherFixture.DEFAULT_LON)
        .forecastedAt(now.minus(2, ChronoUnit.HOURS))
        .forecastAt(now.minus(1, ChronoUnit.HOURS))
        .skyStatus(SkyStatus.CLEAR)
        .precipitationType(PrecipitationType.NONE)
        .precipitationAmount(0.0)
        .precipitationProbability(0.0)
        .temperature(20.0)
        .humidity(50.0)
        .windSpeed(1.5)
        .build();
    Weather w2 = Weather.builder()
        .latitude(WeatherFixture.DEFAULT_LAT)
        .longitude(WeatherFixture.DEFAULT_LON)
        .forecastedAt(now.minus(1, ChronoUnit.HOURS))
        .forecastAt(now)
        .skyStatus(SkyStatus.CLOUDY)
        .precipitationType(PrecipitationType.RAIN)
        .precipitationAmount(10.0)
        .precipitationProbability(70.0)
        .temperature(18.0)
        .humidity(80.0)
        .windSpeed(3.0)
        .build();
    weatherRepository.save(w1);
    weatherRepository.save(w2);

    // when
    Optional<Weather> latest = weatherRepository.findTopByLatitudeAndLongitudeOrderByForecastAtDesc(
        WeatherFixture.DEFAULT_LAT,
        WeatherFixture.DEFAULT_LON
    );

    // then
    assertThat(latest).isPresent();
    assertThat(latest.get().getTemperature()).isEqualTo(18.0);
    assertThat(latest.get().getPrecipitationType()).isEqualTo(PrecipitationType.RAIN);
  }

  @Test
  @DisplayName("기간 내 날씨 예보 목록 조회가 정상 동작한다.")
  void findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc() {
    // given
    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    Weather w1 = WeatherFixture.createCustom(21.0, SkyStatus.CLEAR, PrecipitationType.NONE);
    weatherRepository.save(w1);

    // when
    List<Weather> list = weatherRepository.findAllByLatitudeAndLongitudeAndForecastAtBetweenOrderByForecastAtAsc(
        WeatherFixture.DEFAULT_LAT,
        WeatherFixture.DEFAULT_LON,
        base.minus(2, ChronoUnit.HOURS),
        base.plus(2, ChronoUnit.HOURS)
    );

    // then
    assertThat(list).isNotEmpty();
  }

}
