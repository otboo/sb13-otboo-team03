package com.otboo.team03.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 외부 REST API 통신을 위한 RestClient 공통 설정 클래스
 */
@Configuration
public class RestClientConfig {

  /**
   * RestClient 인스턴스를 빌드하기 위한 Builder 빈 등록
   */
  @Bean
  public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }
}
