package com.otboo.team03.global.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3 저장 모드일 때만 S3 관련 설정과 객체를 등록한다.
 * 이미지를 어디에 저장할지 고르는 설정값
 *
 * AWS 인증 정보는 SDK의 기본인증 조회 방식을 사용한다.
 */
@Configuration
@ConditionalOnProperty(
        prefix = "otboo",
        name = "storage",
        havingValue = "s3"
)
//버킷·리전·URL 유효기간 읽기
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {

    /**
     * 이미지 업로드, 삭제 요청에 사용한다.
     * 애플리케이션 종료 시 연결 자원을 정리한다.
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(S3Properties properties){
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .build();
    }

    /**
     * 비공개 이미지에 접근할 수 있는 임시 서명 URL을 만든다.
     * 파일 업로드나 다운로드 자체를 수행하는 객체는 아니다.
     */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(S3Properties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}