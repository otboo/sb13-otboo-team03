package com.otboo.team03.global.exception;

import java.time.Instant;

public record ErrorResponse (
        Instant timestamp,
        int status,
        String code,
        String message
) {
    public static ErrorResponse from(ErrorCode errorCode) {
        return new ErrorResponse(
                Instant.now(),
                errorCode.getHttpStatus().value(),
                errorCode.name(),
                errorCode.getMessage()
        );
    }
}
