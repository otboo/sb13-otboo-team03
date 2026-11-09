package com.otboo.team03.weather.controller;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weathers")
@RequiredArgsConstructor
public class WeatherController {

  private final LocationService locationService;

  // 위도와 경도를 받아 기상청 격자(x, y)와 행정구역명이 포함된 위치 정보를 반환합니다.
  @GetMapping("/location")
  public ResponseEntity<WeatherAPILocation> getLocation(
      @RequestParam("latitude") Double latitude,
      @RequestParam("longitude") Double longitude
  ) {
    WeatherAPILocation location = locationService.getLocation(latitude, longitude);
    return ResponseEntity.ok(location);
  }


}
