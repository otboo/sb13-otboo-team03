package com.otboo.team03.weather.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.City;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.Coord;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.ForecastItem;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.Main;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.Rain;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.Snow;
import com.otboo.team03.weather.client.dto.OpenWeatherForecastResponse.WeatherCondition;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenWeatherForecastResponseTest {

  @Test
  @DisplayName("OpenWeatherMap DTO 레코드들의 필드와 생성자가 정상 동작한다")
  void dtoRecords_test() {
    Coord coord = new Coord(37.5665, 126.9780);
    assertThat(coord.lat()).isEqualTo(37.5665);
    assertThat(coord.lon()).isEqualTo(126.9780);

    City city = new City(coord);
    assertThat(city.coord()).isEqualTo(coord);

    Snow snow = new Snow(2.5);
    assertThat(snow.threeHour()).isEqualTo(2.5);

    Rain rain = new Rain(3.0);
    assertThat(rain.threeHour()).isEqualTo(3.0);

    Main main = new Main(20.0, 50.0);
    WeatherCondition condition = new WeatherCondition(800, "Clear", "clear sky");
    ForecastItem item = new ForecastItem(1731900000L, main, List.of(condition), null, 0.5, rain, snow);

    OpenWeatherForecastResponse response = new OpenWeatherForecastResponse("200", 0, 1, List.of(item), city);
    assertThat(response.cod()).isEqualTo("200");
    assertThat(response.message()).isEqualTo(0);
    assertThat(response.cnt()).isEqualTo(1);
    assertThat(response.list()).hasSize(1);
    assertThat(response.city()).isEqualTo(city);
  }


}