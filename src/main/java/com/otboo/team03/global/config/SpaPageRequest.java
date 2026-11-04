package com.otboo.team03.global.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/** 브라우저의 페이지 탐색만 프런트 라우터로 전달한다. */
public final class SpaPageRequest {
    private static final List<PathPattern> EXCLUDED_PATHS = List.of(
            "/api/**", "/assets/**", "/swagger-ui/**", "/v3/**", "/error/**"
    ).stream().map(PathPatternParser.defaultInstance::parse).toList();

    private SpaPageRequest() {}

    public static boolean matches(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            return false;
        }
        // MVC와 동일하게 세그먼트별 디코딩과 경로 파라미터 처리를 적용한다.
        PathContainer path = RequestPath.parse(request.getRequestURI(), request.getContextPath())
                .pathWithinApplication();
        for (PathPattern excluded : EXCLUDED_PATHS) {
            if (excluded.matches(path)) return false;
        }
        if (path.elements().stream().anyMatch(element -> element instanceof PathContainer.PathSegment segment
                && segment.valueToMatch().contains("."))) return false;
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
