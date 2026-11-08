package com.otboo.team03.weather.client;

import com.otboo.team03.weather.client.dto.KakaoRegionResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KakaoLocalClient {

  private static final String BASE_URL = "https://dapi.kakao.com";
  private final RestClient restClient;
  public KakaoLocalClient(
      RestClient.Builder restClientBuilder,
      @Value("${kakao.api.key:}") String apiKey
  ) {
    this.restClient = restClientBuilder
        .baseUrl(BASE_URL)
        .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
        .build();
  }

  // 위도(latitude)와 경도(longitude)를 받아 3단계 행정구역명 목록(시/도, 구/군, 동/읍/면)을 반환합니다.
  // 카카오 API에서는 x=경도, y=위도임을 주의합니다.
  public List<String> getRegionNames(Double latitude, Double longitude) {
    if (latitude == null || longitude == null) {
      return Collections.emptyList();
    }

    try {
      KakaoRegionResponse response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path("/v2/local/geo/coord2regioncode.json")
              .queryParam("x", longitude)
              .queryParam("y", latitude)
              .build())
          .retrieve()
          .body(KakaoRegionResponse.class);

      if (response == null || response.documents() == null || response.documents().isEmpty()) {
        return Collections.emptyList();
      }

      // 생활권 기준인 행정동("H") 우선 선택, 없으면 첫 번째 문서 선택
      KakaoRegionResponse.Document document = response.documents().stream()
          .filter(doc -> "H".equalsIgnoreCase(doc.regionType()))
          .findFirst()
          .orElse(response.documents().get(0));

      List<String> result = new ArrayList<>();
      addIfPresent(result, document.region1depthName());
      addIfPresent(result, document.region2depthName());
      addIfPresent(result, document.region3depthName());
      addIfPresent(result, document.region4depthName());

      return result;
    } catch (Exception e) {
      // 외부 카카오 API 장애 시 전체 서비스가 다운되지 않도록 에러 로깅 후 빈 리스트 반환 (장애 격리)
      log.error("카카오 로컬 API 호출 실패: lat={}, lon={}", latitude, longitude, e);
      return Collections.emptyList();
    }
  }

  private void addIfPresent(List<String> list, String name) {
    if (name != null && !name.isBlank()) {
      list.add(name);
    }
  }
}
