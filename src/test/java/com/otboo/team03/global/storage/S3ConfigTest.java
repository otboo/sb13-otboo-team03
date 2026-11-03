package com.otboo.team03.global.storage;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

/** 설정 바인딩과 조건부 Bean 등록을 검증한다. 실제 S3 요청은 보내지 않는다. */
class S3ConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(S3Config.class);

    @Test
    void s3ModeRegistersClientsAndBindsProperties() {
        runner.withPropertyValues("otboo.storage=s3", "otboo.s3.bucket=test-bucket",
                "otboo.s3.region=ap-northeast-2", "otboo.s3.presign-minutes=5")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(S3Client.class)
                            .hasSingleBean(S3Presigner.class).hasSingleBean(S3Properties.class);
                    assertThat(context.getBean(S3Properties.class))
                            .isEqualTo(new S3Properties("test-bucket", "ap-northeast-2", 5));
                });
    }

    @Test
    void localModeDoesNotRequireS3Settings() {
        runner.withPropertyValues("otboo.storage=local").run(context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(S3Client.class)
                        .doesNotHaveBean(S3Presigner.class).doesNotHaveBean(S3Properties.class));
    }

    @Test
    void invalidS3SettingsFailStartup() {
        runner.withPropertyValues("otboo.storage=s3", "otboo.s3.bucket=",
                "otboo.s3.region=ap-northeast-2", "otboo.s3.presign-minutes=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
