package com.otboo.team03.weather.service;

import com.otboo.team03.profile.dto.WeatherAPILocation;
import com.otboo.team03.weather.client.KakaoLocalClient;
import com.otboo.team03.weather.util.WeatherGrid;
import com.otboo.team03.weather.util.WeatherGridConverter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LocationService {

  private final KakaoLocalClient kakaoLocalClient;

  // 위도와 경도를 받아 기상청 격자(x, y)와 카카오 행정구역명을 결합한 WeatherAPILocation을 생성합니다.
  public WeatherAPILocation getLocation(Double latitude, Double longitude) {
    if (latitude == null || longitude == null) {
      return null;
    }

    // 1. 기상청 람베르트 정각원추 투영법으로 격자 x, y 계산
    WeatherGrid grid = WeatherGridConverter.toGrid(latitude, longitude);

    // 2. 카카오 로컬 REST API로 3단계 행정동 지명 조회
    List<String> locationNames = kakaoLocalClient.getRegionNames(latitude, longitude);

    // 3. 최종 DTO 조립 반환
    return new WeatherAPILocation(
        latitude,
        longitude,
        grid.x(),
        grid.y(),
        locationNames
    );
  }
}