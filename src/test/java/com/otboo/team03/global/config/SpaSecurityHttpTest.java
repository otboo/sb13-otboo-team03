package com.otboo.team03.global.config;

import com.otboo.team03.global.security.OtbooUserDetailsService;
import com.otboo.team03.global.security.jwt.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = SpaSecurityHttpTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpaSecurityHttpTest {
    @LocalServerPort
    int port;

    @Test
    void encodedApiRequestsCannotBypassAuthentication() throws Exception {
        {
            HttpClient client = HttpClient.newHttpClient();
            for (String path : new String[]{"/api/clothes/123", "/%61pi/clothes/123", "/a%70i/clothes/123"}) {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Accept", "text/html, application/json").GET().build();
                assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
                        .as(path).isEqualTo(401);
            }
        }
        assertThat(Api.calls.get()).isZero();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
    })
    @Import({SecurityConfig.class, Api.class})
    static class Application {
        @Bean JwtLoginSuccessHandler successHandler() { return mock(JwtLoginSuccessHandler.class); }
        @Bean JwtLoginFailureHandler failureHandler() { return mock(JwtLoginFailureHandler.class); }
        @Bean JwtLogoutHandler logoutHandler() { return mock(JwtLogoutHandler.class); }
        @Bean JwtAuthenticationFilter jwtFilter() {
            return new JwtAuthenticationFilter(mock(JwtTokenProvider.class), mock(JwtRegistry.class),
                    mock(OtbooUserDetailsService.class));
        }
        @Bean AuthenticationEntryPoint entryPoint() {
            return (request, response, exception) -> response.setStatus(401);
        }
        @Bean AccessDeniedHandler deniedHandler() {
            return (request, response, exception) -> response.setStatus(403);
        }
    }

    @RestController
    static class Api {
        static final AtomicInteger calls = new AtomicInteger();
        @GetMapping("/api/clothes/{id}")
        String clothes() { calls.incrementAndGet(); return "clothes"; }
    }
}
