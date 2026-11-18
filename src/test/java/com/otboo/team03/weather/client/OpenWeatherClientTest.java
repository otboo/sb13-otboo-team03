package com.otboo.team03.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import com.otboo.team03.weather.entity.SkyStatus;
import com.otboo.team03.weather.entity.Weather;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenWeatherClientTest {

  private MockRestServiceServer mockServer;
  private OpenWeatherClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    OpenWeatherMapper mapper = new OpenWeatherMapper();
    client = new OpenWeatherClient(builder, mapper, "https://api.openweathermap.org", "test-api-key");
  }

  @Test
  @DisplayName("위도와 경도로 날씨 예보를 요청하면 200 OK 응답을 받아 List<Weather>로 변환한다")
  void fetchForecast_success() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;

    String mockResponseBody = """
        {
          "cod": "200",
          "message": 0,
          "cnt": 1,
          "list": [
            {
              "dt": 1731900000,
              "main": {
                "temp": 15.0,
                "humidity": 60.0
              },
              "weather": [
                {
                  "id": 800,
                  "main": "Clear",
                  "description": "clear sky"
                }
              ],
              "wind": {
                "speed": 2.5
              },
              "pop": 0.2
            }
          ]
        }
        """;

    mockServer.expect(requestTo(containsString("/data/2.5/forecast")))
        .andExpect(requestTo(containsString("lat=37.5665")))
        .andExpect(requestTo(containsString("lon=126.978")))
        .andExpect(requestTo(containsString("appid=test-api-key")))
        .andExpect(requestTo(containsString("units=metric")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

    // when
    List<Weather> result = client.fetchForecast(latitude, longitude);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getTemperature()).isEqualTo(15.0);
    assertThat(result.get(0).getSkyStatus()).isEqualTo(SkyStatus.CLEAR);
    assertThat(result.get(0).getPrecipitationProbability()).isEqualTo(20.0);
    mockServer.verify();
  }

  @Test
  @DisplayName("위도 또는 경도가 null이면 API를 호출하지 않고 빈 목록을 반환한다")
  void fetchForecast_nullCoordinates_returnsEmptyList() {
    assertThat(client.fetchForecast(null, 126.9780)).isEmpty();
    assertThat(client.fetchForecast(37.5665, null)).isEmpty();
  }

  @Test
  @DisplayName("OpenWeatherMap API 호출 중 서버 에러(500)가 발생해도 예외를 던지지 않고 빈 목록을 반환한다 (장애 격리)")
  void fetchForecast_serverError_returnsEmptyList() {
    mockServer.expect(requestTo(containsString("/data/2.5/forecast")))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withServerError());

    List<Weather> result = client.fetchForecast(37.5665, 126.9780);

    assertThat(result).isEmpty();
    mockServer.verify();
  }


}