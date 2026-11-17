package com.otboo.team03.weather.service;

import com.otboo.team03.weather.dto.WeatherDto;
import java.util.List;

public interface WeatherService {

  List<WeatherDto> getWeathers(Double longitude, Double latitude);

}