package com.otboo.team03.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 로컬 .env 및 개발 데이터와 분리된 테스트 JVM 전용 DB. 종료 시 Ryuk이 정리한다. */
@ActiveProfiles("test")
public abstract class PostgresTestSupport {
    private static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine");

    static {
        DATABASE.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("otboo.storage", () -> "local");
    }
}
