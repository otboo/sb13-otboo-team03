package com.otboo.team03.weather.init;

import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.repository.WeatherRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WeatherInitializerTest {

  @Mock
  private WeatherRepository weatherRepository;

  @InjectMocks
  private WeatherInitializer weatherInitializer;

  @Captor
  private ArgumentCaptor<List<Weather>> weatherListCaptor;

  @Test
  @DisplayName("1. DB에 유효 예보 데이터가 없으면 서울 기준 날씨 Mock 데이터를 생성하여 저장한다")
  void initialize_whenEmpty_savesMockWeatherData() {
    // given
    given(weatherRepository.existsByForecastAtAfter(any())).willReturn(false);

    // when
    weatherInitializer.initialize();

    // then
    verify(weatherRepository).saveAll(weatherListCaptor.capture());
    List<Weather> savedList = weatherListCaptor.getValue();
    assertThat(savedList).isNotEmpty();

    // 서울 기본 좌표 검증
    assertThat(savedList.get(0).getLatitude()).isEqualTo(37.4979);
    assertThat(savedList.get(0).getLongitude()).isEqualTo(127.0276);
    assertThat(savedList.get(0).getTemperature()).isNotNull();
    assertThat(savedList.get(0).getSkyStatus()).isNotNull();
  }

  @Test
  @DisplayName("2. DB에 이미 오늘/미래 유효 예보 데이터가 존재하면 추가 저장하지 않고 종료한다")
  void initialize_whenAlreadyExists_doesNotSave() {
    // given
    given(weatherRepository.existsByForecastAtAfter(any())).willReturn(true);
    // when
    weatherInitializer.initialize();
    // then
    verify(weatherRepository, never()).deleteAll();
    verify(weatherRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("3. 과거 더미데이터만 남아있는 경우 기존 데이터를 정리하고 최신 날씨를 생성한다")
  void initialize_whenOnlyPastDataExists_clearsAndSavesMockWeatherData() {
    // given
    given(weatherRepository.existsByForecastAtAfter(any())).willReturn(false);

    // when
    weatherInitializer.initialize();

    // then
    verify(weatherRepository, never()).deleteAll();
    verify(weatherRepository).saveAll(any());
  }

  @Test
  @DisplayName("4. run 메서드 호출 시 initialize를 정상 실행한다")
  void run_executesInitialize() {
    given(weatherRepository.existsByForecastAtAfter(any())).willReturn(true);
    weatherInitializer.run(null);
    verify(weatherRepository, never()).saveAll(any());
  }


}