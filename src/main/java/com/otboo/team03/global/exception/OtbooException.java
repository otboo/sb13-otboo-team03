package com.otboo.team03.global.exception;

import lombok.Getter;

import java.util.Map;

@Getter
public class OtbooException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> details;

    public OtbooException(ErrorCode errorCode) {
        super(errorCode.getMessage());

        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public OtbooException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);

        this.errorCode = errorCode;
        this.details = Map.of();
    }

    public OtbooException(ErrorCode errorCode, Map<String, String> details) {
        super(errorCode.getMessage());

        this.errorCode = errorCode;
        this.details = Map.copyOf(details);
    }

    public OtbooException(ErrorCode errorCode, Throwable cause, Map<String, String> details) {
        super(errorCode.getMessage(), cause);

        this.errorCode = errorCode;
        this.details = Map.copyOf(details);
    }

}
