package com.otboo.team03.global.exception;

import java.util.Map;

public record ErrorResponse (
        String exceptionName,
        String message,
        Map<String, String> details
) {
    public static ErrorResponse from(ErrorCode errorCode, Map<String, String> details) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                details
        );
    }

    public static ErrorResponse from(ErrorCode errorCode) {
        return from(errorCode, Map.of());
    }
}
