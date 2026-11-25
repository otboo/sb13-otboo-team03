package com.otboo.team03.global.exception.llm;

import java.util.Set;
import lombok.Getter;
import org.springframework.web.client.RestClientException;

/** 원본 응답이나 원인 예외를 보관하지 않고 안전한 Groq HTTP 진단 정보만 전달한다. */
@Getter
public class GroqRequestException extends RestClientException {

    private static final Set<String> ERROR_TYPES = Set.of(
            "invalid_request_error", "authentication_error", "permission_error",
            "rate_limit_error", "server_error");
    private static final Set<String> ERROR_CODES = Set.of(
            "json_validate_failed", "tool_use_failed", "context_length_exceeded",
            "invalid_api_key", "model_not_found", "rate_limit_exceeded");

    private final int status;
    private final String errorType;
    private final String errorCode;

    public GroqRequestException(int status, String errorType, String errorCode) {
        super("Groq HTTP 요청에 실패했습니다.");
        this.status = status;
        // 형식 검사만으로는 키나 옷 제목을 걸러낼 수 없으므로 알려진 값만 허용한다.
        this.errorType = errorType != null && ERROR_TYPES.contains(errorType) ? errorType : "unknown";
        this.errorCode = errorCode != null && ERROR_CODES.contains(errorCode) ? errorCode : "unknown";
    }
}
