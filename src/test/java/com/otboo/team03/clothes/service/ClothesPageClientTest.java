package com.otboo.team03.clothes.service;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClothesPageClientTest {
    private final ClothesPageClient client = new ClothesPageClient(new PurchaseUrlValidator());
    private final String url = "https://www.musinsa.com/products/1";

    @ParameterizedTest
    @ValueSource(strings = {"https://www.musinsa.com", "https://www.musinsa.com/login",
            "https://product.29cm.co.kr/", "https://www.29cm.co.kr/search?keyword=shirt",
            "https://www.wconcept.co.kr/", "https://www.musinsa.com/products/123/../login",
            "https://www.musinsa.com/products/not-a-product", "https://www.musinsa.com/products/123%2Flogin"})
    void rejectsNonProductPathsBeforeNetwork(String input) {
        assertThatThrownBy(() -> client.fetch(input)).isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PURCHASE_URL));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"https://[invalid", "https:/products/1", "", " ", "broken", "file:///etc/passwd", "http://www.musinsa.com/products/1",
            "https://user:pass@www.musinsa.com/a", "https://www.musinsa.com:8443/a", "https://www.musinsa.com/a#b"})
    void rejectsInvalidUrls(String input) {
        assertThatThrownBy(() -> client.fetch(input)).isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PURCHASE_URL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost/a", "https://127.0.0.1/a", "https://169.254.169.254/a",
            "https://www.musinsa.com.evil.example/a", "https://evil.example/?url=https://www.musinsa.com"})
    void rejectsUnlistedHostsBeforeNetwork(String input) {
        assertThatThrownBy(() -> client.fetch(input)).isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNSUPPORTED_SHOPPING_SITE));
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 403, 404, 429, 500})
    void rejectsRedirectsAndHttpErrors(int status) throws Exception {
        try (var jsoup = mockStatic(Jsoup.class)) {
            Connection connection = mock(Connection.class, RETURNS_SELF);
            Connection.Response response = mock(Connection.Response.class);
            jsoup.when(() -> Jsoup.connect(url)).thenReturn(connection);
            when(connection.execute()).thenReturn(response);
            when(response.statusCode()).thenReturn(status);
            ErrorCode expected = status == 403 ? ErrorCode.MUSINSA_ACCESS_RESTRICTED : ErrorCode.CLOTHES_EXTRACTION_FAILED;
            assertThatThrownBy(() -> client.fetch(url)).isInstanceOfSatisfying(OtbooException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(expected));
            verify(connection).followRedirects(false);
        }
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"text/html; charset=UTF-8", "application/json", "oversize"})
    void checksContentTypeAndBodyLimit(String contentType) throws Exception {
        try (var jsoup = mockStatic(Jsoup.class)) {
            Connection connection = mock(Connection.class, RETURNS_SELF);
            Connection.Response response = mock(Connection.Response.class);
            jsoup.when(() -> Jsoup.connect(url)).thenReturn(connection);
            when(connection.execute()).thenReturn(response);
            when(response.statusCode()).thenReturn(200);
            when(response.contentType()).thenReturn("oversize".equals(contentType) ? "text/html" : contentType);
            when(response.bodyAsBytes()).thenReturn(new byte["oversize".equals(contentType) ? 2 * 1024 * 1024 + 1 : 10]);
            when(response.body()).thenReturn("<html></html>");
            if (contentType != null && contentType.startsWith("text/html")) {
                jsoup.when(() -> Jsoup.parse("<html></html>", url)).thenCallRealMethod();
                assertThat(client.fetch(url).location()).isEqualTo(url);
            } else {
                assertThatThrownBy(() -> client.fetch(url)).isInstanceOf(OtbooException.class);
            }
        }
    }

    @Test
    void 다른_쇼핑몰의_403에는_무신사_안내를_반환하지_않는다() throws Exception {
        String shopUrl = "https://www.29cm.co.kr/products/123";
        try (var jsoup = mockStatic(Jsoup.class)) {
            Connection connection = mock(Connection.class, RETURNS_SELF);
            Connection.Response response = mock(Connection.Response.class);
            jsoup.when(() -> Jsoup.connect(shopUrl)).thenReturn(connection);
            when(connection.execute()).thenReturn(response);
            when(response.statusCode()).thenReturn(403);
            assertThatThrownBy(() -> client.fetch(shopUrl)).isInstanceOfSatisfying(OtbooException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_FAILED));
        }
    }

    @Test
    void mapsTimeoutToExtractionFailure() throws Exception {
        try (var jsoup = mockStatic(Jsoup.class)) {
            Connection connection = mock(Connection.class, RETURNS_SELF);
            jsoup.when(() -> Jsoup.connect(url)).thenReturn(connection);
            when(connection.execute()).thenThrow(new java.net.SocketTimeoutException());
            assertThatThrownBy(() -> client.fetch(url)).isInstanceOfSatisfying(OtbooException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CLOTHES_EXTRACTION_FAILED));
        }
    }
    @Test
    void rejectsUrlExceedingLengthLimitBeforeNetwork() {
        String input = url + "?q=" + "a".repeat(2048);
        assertThatThrownBy(() -> client.fetch(input)).isInstanceOfSatisfying(OtbooException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_PURCHASE_URL));
    }

}
