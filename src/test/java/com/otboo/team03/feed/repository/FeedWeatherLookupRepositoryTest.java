package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import com.otboo.team03.weather.entity.PrecipitationType;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import com.otboo.team03.weather.exception.WeatherNotFoundException;
import com.otboo.team03.weather.repository.WeatherRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedWeatherLookupRepositoryTest {

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private JdbcTemplate jdbcTemplate;

  @InjectMocks
  private FeedWeatherLookupRepository repository;

  @Test
  void 날씨_존재_여부를_WeatherRepository로_확인한다() {
    // given
    UUID weatherId = UUID.randomUUID();

    when(weatherRepository.existsById(weatherId))
        .thenReturn(true);

    // when
    boolean exists = repository.existsById(weatherId);

    // then
    assertTrue(exists);
    verify(weatherRepository).existsById(weatherId);
    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void Weather_엔티티를_기존_날씨_요약_DTO로_변환한다() {
    // given
    UUID weatherId = UUID.randomUUID();
    Weather weather = mock(Weather.class);

    when(weatherRepository.findById(weatherId))
        .thenReturn(Optional.of(weather));
    when(weather.getId()).thenReturn(weatherId);
    when(weather.getSkyStatus()).thenReturn(SkyStatus.CLOUDY);
    when(weather.getPrecipitationType()).thenReturn(PrecipitationType.RAIN);
    when(weather.getPrecipitationAmount()).thenReturn(5.5);
    when(weather.getPrecipitationProbability()).thenReturn(60.0);
    when(weather.getTemperature()).thenReturn(18.0);

    // when
    WeatherSummaryDto result = repository.findSummaryById(weatherId);

    // then
    assertAll(
        () -> assertEquals(weatherId, result.weatherId()),
        () -> assertEquals("CLOUDY", result.skyStatus()),
        () -> assertEquals("RAIN", result.precipitation().type()),
        () -> assertEquals(5.5, result.precipitation().amount()),
        () -> assertEquals(60.0, result.precipitation().probability()),
        () -> assertEquals(18.0, result.temperature().current()),
        () -> assertNull(result.temperature().comparedToDayBefore()),
        () -> assertNull(result.temperature().min()),
        () -> assertNull(result.temperature().max())
    );

    verify(weatherRepository).findById(weatherId);
    verifyNoInteractions(jdbcTemplate);
  }

  @Test
  void 존재하지_않는_날씨는_날씨_예외를_발생시킨다() {
    // given
    UUID weatherId = UUID.randomUUID();

    when(weatherRepository.findById(weatherId))
        .thenReturn(Optional.empty());

    // when
    WeatherNotFoundException exception = assertThrows(
        WeatherNotFoundException.class,
        () -> repository.findSummaryById(weatherId)
    );

    // then
    assertNotNull(exception);
    verify(weatherRepository).findById(weatherId);
    verifyNoInteractions(jdbcTemplate);
  }
}
