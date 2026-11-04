package com.otboo.team03.global.storage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yaml의 otboo.s3설정을 읽는다.
 * AWS 비밀키는 이 클래스에 보관하지 않음!!!
 */
@Validated
@ConfigurationProperties(prefix = "otboo.s3")
public record S3Properties(

        @NotBlank
        String bucket,

        @NotBlank
        String region,

        //서명 URL 유효기간, 운영 환경에서는 임시 인증 정보의 만료에도 영향을 받는다.
        @Min(1)
        @Max(10080)
        long presignMinutes
) {
}
