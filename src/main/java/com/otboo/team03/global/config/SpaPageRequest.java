package com.otboo.team03.global.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;

/** 브라우저의 페이지 탐색만 프런트 라우터로 전달한다. */
public final class SpaPageRequest {
    private SpaPageRequest() {}

    public static boolean matches(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (String prefix : new String[]{"/api", "/assets", "/swagger-ui", "/v3", "/error"}) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) return false;
        }
        if (path.contains(".")) return false;
        String accept = request.getHeader("Accept");
        if (accept == null) return false;
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(type -> !type.isWildcardType() && !type.isWildcardSubtype()
                            && type.isCompatibleWith(MediaType.TEXT_HTML) && type.getQualityValue() > 0);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
