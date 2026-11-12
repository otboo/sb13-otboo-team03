package com.otboo.team03.global.security;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import org.springframework.security.core.Authentication;

import java.util.UUID;

public final class AuthenticatedUserIdExtractor {

    private AuthenticatedUserIdExtractor() {
    }

    public static UUID extract(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof OtbooUserDetails userDetails)) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }

        return userDetails.getUserDto().id();
    }
}
