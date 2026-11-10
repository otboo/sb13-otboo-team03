package com.otboo.team03.weather.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record KakaoRegionResponse(
    @JsonProperty("meta") Meta meta,
    @JsonProperty("documents") List<Document> documents
) {
  public record Meta(
      @JsonProperty("total_count") int totalCount
  ) {}
  public record Document(
      @JsonProperty("region_type") String regionType, // "H"(행정동) or "B"(법정동)
      @JsonProperty("address_name") String addressName,
      @JsonProperty("region_1depth_name") String region1depthName, // 시/도 (예: 서울특별시)
      @JsonProperty("region_2depth_name") String region2depthName, // 구/군 (예: 중구)
      @JsonProperty("region_3depth_name") String region3depthName, // 동/읍/면 (예: 명동)
      @JsonProperty("region_4depth_name") String region4depthName
  ) {}
}
