package com.otboo.team03.weather.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

// 요청한 날씨 데이터를 찾을 수 없을 때 발생하는 비즈니스 예외
public class WeatherNotFoundException extends OtbooException {
  public WeatherNotFoundException() {
    super(ErrorCode.NOT_FOUND_WEATHER);
  }
}