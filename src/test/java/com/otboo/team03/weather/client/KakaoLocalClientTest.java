package com.otboo.team03.weather.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoLocalClientTest {

  private MockRestServiceServer mockServer;
  private KakaoLocalClient kakaoLocalClient;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    mockServer = MockRestServiceServer.bindTo(builder).build();
    kakaoLocalClient = new KakaoLocalClient(builder, "test-kakao-api-key");
  }

  @Test
  @DisplayName("위도와 경도로 카카오 로컬 API를 호출하면 행정구역명 3단계 목록을 반환한다")
  void getRegionNames_success() {
    // given
    double latitude = 37.5665;
    double longitude = 126.9780;

    String mockResponseBody = """
        {
          "meta": { "total_count": 2 },
          "documents": [
            {
              "region_type": "B",
              "region_1depth_name": "서울특별시",
              "region_2depth_name": "중구",
              "region_3depth_name": "태평로1가",
              "region_4depth_name": ""
            },
            {
              "region_type": "H",
              "region_1depth_name": "서울특별시",
              "region_2depth_name": "중구",
              "region_3depth_name": "명동",
              "region_4depth_name": ""
            }
          ]
        }
        """;

    mockServer.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=126.978&y=37.5665"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-kakao-api-key"))
        .andRespond(withSuccess(mockResponseBody, MediaType.APPLICATION_JSON));

    // when
    List<String> regionNames = kakaoLocalClient.getRegionNames(latitude, longitude);

    // then: 행정동("H") 기준으로 3단계 지명이 추출되었는지 검증!
    assertThat(regionNames).containsExactly("서울특별시", "중구", "명동");
    mockServer.verify();
  }

  @Test
  @DisplayName("위도 또는 경도가 null이면 빈 목록을 반환한다")
  void getRegionNames_nullCoordinates_returnsEmptyList() {
    assertThat(kakaoLocalClient.getRegionNames(null, 126.9780)).isEmpty();
    assertThat(kakaoLocalClient.getRegionNames(37.5665, null)).isEmpty();
  }

  @Test
  @DisplayName("카카오 API 응답의 documents가 비어있으면 빈 목록을 반환한다")
  void getRegionNames_emptyDocuments_returnsEmptyList() {
    String emptyBody = """
        {
          "meta": { "total_count": 0 },
          "documents": []
        }
        """;

    mockServer.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=126.978&y=37.5665"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(emptyBody, MediaType.APPLICATION_JSON));

    List<String> regionNames = kakaoLocalClient.getRegionNames(37.5665, 126.9780);
    assertThat(regionNames).isEmpty();
    mockServer.verify();
  }

  @Test
  @DisplayName("카카오 API 호출 시 서버 에러(500)가 발생해도 예외를 던지지 않고 빈 목록을 반환한다 (장애 격리)")
  void getRegionNames_serverError_returnsEmptyList() {

    mockServer.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=126.978&y=37.5665"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

    List<String> regionNames = kakaoLocalClient.getRegionNames(37.5665, 126.9780);
    assertThat(regionNames).isEmpty();
    mockServer.verify();
  }

  @Test
  @DisplayName("행정동(H) 문서가 없고 법정동(B) 문서만 있어도 첫 번째 문서를 기반으로 정상 반환한다")
  void getRegionNames_onlyLegalDong_success() {
    String onlyLegalBody = """
        {
          "meta": { "total_count": 1 },
          "documents": [
            {
              "region_type": "B",
              "region_1depth_name": "서울특별시",
              "region_2depth_name": "중구",
              "region_3depth_name": "태평로1가",
              "region_4depth_name": ""
            }
          ]
        }
        """;

    mockServer.expect(requestTo("https://dapi.kakao.com/v2/local/geo/coord2regioncode.json?x=126.978&y=37.5665"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(onlyLegalBody, MediaType.APPLICATION_JSON));

    List<String> regionNames = kakaoLocalClient.getRegionNames(37.5665, 126.9780);
    assertThat(regionNames).containsExactly("서울특별시", "중구", "태평로1가");
    mockServer.verify();
  }


}