package com.otboo.team03.global.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class SpaPageRequestTest {
    @ParameterizedTest
    @CsvSource({
            "text/html, true",
            "text/html;q=0, false",
            "text/html;q=0.5, true",
            "text/*, false",
            "*/*, false",
            "application/json, false",
            "invalid, false",
            "text/html;q=invalid, false",
            "'application/json, text/html', true",
            "'text/html;q=0, application/json', false"
    })
    void acceptsOnlyExplicitHtmlWithPositiveQuality(String accept, boolean expected) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/closet");
        request.addHeader("Accept", accept);

        assertThat(SpaPageRequest.matches(request)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "/app/closet, true",
            "/app/%61pi/clothes/123, false",
            "/app/a%70i/clothes/123, false",
            "/app/api;version=1/clothes, false",
            "/app/%61ssets/main.js, false",
            "/app/icon%2Epng, false",
            "/app/api, false",
            "/app/api/users, false",
            "/app/assets, false",
            "/app/swagger-ui, false",
            "/app/v3, false",
            "/app/error, false",
            "/app/error/details, false",
            "/app/icon.png, false",
            "/app/apiary, true"
    })
    void checksPathsRelativeToContext(String uri, boolean expected) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setContextPath("/app");
        request.addHeader("Accept", "text/html");

        assertThat(SpaPageRequest.matches(request)).isEqualTo(expected);
    }
}
