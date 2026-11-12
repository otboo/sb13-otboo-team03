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
  @DisplayName("1. DB에 날씨 데이터가 없으면 서울 기준 날씨 Mock 데이터를 생성하여 저장한다")
  void initialize_whenEmpty_savesMockWeatherData() {
    // given: DB에 날씨가 0건인 상황
    given(weatherRepository.count()).willReturn(0L);

    // when: 초기화 실행
    weatherInitializer.initialize();

    // then: weatherRepository.saveAll(...)이 호출되었고, 데이터가 정상적으로 들어있는지 검증
    verify(weatherRepository).saveAll(weatherListCaptor.capture());
    List<Weather> savedList = weatherListCaptor.getValue();
    assertThat(savedList).isNotEmpty();

    // 서울시청 기본 좌표 검증
    assertThat(savedList.get(0).getLatitude()).isEqualTo(37.4979);
    assertThat(savedList.get(0).getLongitude()).isEqualTo(127.0276);
    assertThat(savedList.get(0).getTemperature()).isNotNull();
    assertThat(savedList.get(0).getSkyStatus()).isNotNull();
  }

  @Test
  @DisplayName("2. DB에 이미 날씨 데이터가 존재하면 추가 저장하지 않고 종료한다")
  void initialize_whenAlreadyExists_doesNotSave() {
    // given: DB에 이미 5건의 날씨 데이터가 있는 상황
    given(weatherRepository.count()).willReturn(5L);

    // when: 초기화 실행
    weatherInitializer.initialize();

    // then
    verify(weatherRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("3. run 메서드 호출 시 initialize를 정상 실행한다")
  void run_executesInitialize() {
    given(weatherRepository.count()).willReturn(1L);
    weatherInitializer.run(null);
    verify(weatherRepository, never()).saveAll(any());
  }


}