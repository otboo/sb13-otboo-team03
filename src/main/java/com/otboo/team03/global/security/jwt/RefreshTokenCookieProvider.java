
package com.otboo.team03.global.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieProvider {

    @Value("${otboo.jwt.cookie-secure:false}")
    private boolean cookieSecure;

    public ResponseCookie create(String refreshToken, Duration maxAge) {
        return ResponseCookie.from("REFRESH_TOKEN", refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    public ResponseCookie delete() {
        return ResponseCookie.from("REFRESH_TOKEN", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
