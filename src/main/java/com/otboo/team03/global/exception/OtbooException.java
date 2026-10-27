package com.otboo.team03.global.exception;

import lombok.Getter;

@Getter
public class OtbooException extends RuntimeException {

    private final ErrorCode errorCode;

    public OtbooException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public OtbooException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
