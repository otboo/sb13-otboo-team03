package com.otboo.team03.weather.client;

import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse;
import com.otboo.team03.weather.entity.Weather;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class OpenWeatherClient {

  private final RestClient restClient;
  private final OpenWeatherMapper openWeatherMapper;
  private final String apiKey;

  public OpenWeatherClient(
      @Autowired(required = false) RestClient.Builder restClientBuilder,
      OpenWeatherMapper openWeatherMapper,
      @Value("${openweather.api.base-url:https://api.openweathermap.org}") String baseUrl,
      @Value("${openweather.api.key:}") String apiKey
  ) {
    RestClient.Builder builder = (restClientBuilder != null) ? restClientBuilder : RestClient.builder();
    this.restClient = builder.baseUrl(baseUrl).build();
    this.openWeatherMapper = openWeatherMapper;
    this.apiKey = apiKey;
  }

  // 위도, 경도를 받아 5일간의 3시간 간격 예보를 조회하고 List<Weather>로 반환
  public List<Weather> fetchForecast(Double latitude, Double longitude) {
    if (latitude == null || longitude == null) {
      return Collections.emptyList();
    }

    try {
      OpenWeatherForecastResponse response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/data/2.5/forecast")
              .queryParam("lat", latitude)
              .queryParam("lon", longitude)
              .queryParam("appid", apiKey)
              .queryParam("units", "metric") // 섭씨(℃) 온도
              .build())
          .retrieve()
          .body(OpenWeatherForecastResponse.class);

      return openWeatherMapper.toWeatherEntities(response, latitude, longitude);
    } catch (Exception e) {
      log.error("OpenWeatherMap API 호출 실패 - lat: {}, lon: {}, message: {}", latitude, longitude, e.getMessage());
      return Collections.emptyList();
    }
  }

}
