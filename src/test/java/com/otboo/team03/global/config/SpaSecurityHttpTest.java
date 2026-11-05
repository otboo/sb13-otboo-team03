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
import static org.mockito.Mockito.*;
import com.otboo.team03.clothes.controller.ClothesController;
import com.otboo.team03.clothes.service.ClothesService;
import com.otboo.team03.global.exception.GlobalExceptionHandler;
import com.otboo.team03.global.security.OtbooUserDetails;
import com.otboo.team03.user.dto.response.UserDto;
import com.otboo.team03.user.entity.UserRole;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.net.CookieManager;
import java.net.CookiePolicy;

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
        verify(clothesService, never()).findById(any());
    }

    @Autowired ClothesService clothesService;
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void realMultipartLimitRejectsOversizeBeforeService() throws Exception {
        clearInvocations(clothesService);
        var s3 = mock(software.amazon.awssdk.services.s3.S3Client.class);
        var storage = new com.otboo.team03.global.storage.S3ImageStorage(s3,
                mock(software.amazon.awssdk.services.s3.presigner.S3Presigner.class),
                new com.otboo.team03.global.storage.S3Properties("test-bucket", "ap-northeast-2", 5));
        doAnswer(invocation -> {
            storage.upload(invocation.getArgument(1));
            return null;
        }).when(clothesService).create(any(), any());
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> csrf = client.send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/test-csrf"))
                .header("Authorization", "Bearer test-token").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(csrf.statusCode()).isEqualTo(200);
        for (int size : new int[]{5 * 1024 * 1024, 5 * 1024 * 1024 + 1}) {
            byte[] prefix = ("--boundary\r\nContent-Disposition: form-data; name=\"request\"\r\n"
                    + "Content-Type: application/json\r\n\r\n"
                    + "{\"ownerId\":\"" + OWNER + "\",\"name\":\"shirt\",\"type\":\"TOP\","
                    + "\"attributes\":[{\"definitionId\":\"" + OWNER + "\",\"value\":\"blue\"}]}"
                    + "\r\n--boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"image.png\""
                    + "\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] suffix = "\r\n--boundary--\r\n".getBytes(StandardCharsets.UTF_8);
            byte[] image = validPng(size);
            assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image))).isNotNull();
            byte[] body = new byte[prefix.length + size + suffix.length];
            System.arraycopy(prefix, 0, body, 0, prefix.length);
            System.arraycopy(image, 0, body, prefix.length, image.length);
            System.arraycopy(suffix, 0, body, prefix.length + size, suffix.length);
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/clothes"))
                    .header("Authorization", "Bearer test-token")
                    .header("X-XSRF-TOKEN", cookies.getCookieStore().getCookies().stream()
                            .filter(cookie -> cookie.getName().equals("XSRF-TOKEN"))
                            .findFirst().orElseThrow().getValue())
                    .header("Content-Type", "multipart/form-data; boundary=boundary")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("size=%s body=%s", size, response.body())
                    .isEqualTo(size == 5 * 1024 * 1024 ? 201 : 400);
        }
        verify(clothesService, times(1)).create(any(), any());
        verify(s3, times(1)).putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
                any(software.amazon.awssdk.core.sync.RequestBody.class));
        verifyNoMoreInteractions(s3);
        reset(clothesService);
    }

    /** PNG에 CRC가 유효한 ancillary chunk를 넣어 정확한 경계 크기를 만든다. */
    private byte[] validPng(int size) throws Exception {
        var original = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_RGB), "png", original);
        byte[] png = original.toByteArray();
        int padding = size - png.length - 12;
        var output = new java.io.ByteArrayOutputStream(size);
        var data = new java.io.DataOutputStream(output);
        data.write(png, 0, png.length - 12);
        data.writeInt(padding);
        byte[] type = "paDd".getBytes(StandardCharsets.US_ASCII);
        byte[] content = new byte[padding];
        data.write(type);
        data.write(content);
        var crc = new java.util.zip.CRC32();
        crc.update(type);
        crc.update(content);
        data.writeInt((int) crc.getValue());
        data.write(png, png.length - 12, 12);
        return output.toByteArray();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"
    })
    @Import({SecurityConfig.class, Api.class, ClothesController.class, GlobalExceptionHandler.class})
    static class Application {
        @Bean ClothesService clothesService() { return mock(ClothesService.class); }
        @Bean JwtLoginSuccessHandler successHandler() { return mock(JwtLoginSuccessHandler.class); }
        @Bean JwtLoginFailureHandler failureHandler() { return mock(JwtLoginFailureHandler.class); }
        @Bean JwtLogoutHandler logoutHandler() { return mock(JwtLogoutHandler.class); }
        @Bean JwtAuthenticationFilter jwtFilter() {
            JwtTokenProvider provider = mock(JwtTokenProvider.class);
            JwtRegistry registry = mock(JwtRegistry.class);
            OtbooUserDetailsService users = mock(OtbooUserDetailsService.class);
            when(provider.getUserId("test-token")).thenReturn(OWNER);
            when(registry.hasActiveJwtInformationByAccessToken("test-token")).thenReturn(true);
            when(users.loadUserById(OWNER)).thenReturn(new OtbooUserDetails(
                    new UserDto(OWNER, Instant.now(), "test@example.com", "test", UserRole.USER, false), "password"));
            return new JwtAuthenticationFilter(provider, registry, users);
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
        @GetMapping("/api/test-csrf")
        String csrf(CsrfToken token) { return token.getToken(); }
        @GetMapping("/api/test-clothes/{id}")
        String clothes() { calls.incrementAndGet(); return "clothes"; }
    }
}
