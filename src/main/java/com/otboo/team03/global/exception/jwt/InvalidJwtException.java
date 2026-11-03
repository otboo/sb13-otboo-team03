package com.otboo.team03.global.exception.jwt;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class InvalidJwtException extends OtbooException {

    public InvalidJwtException() {
        super(ErrorCode.INVALID_JWT);
    }

    public InvalidJwtException(Throwable cause) {
        super(ErrorCode.INVALID_JWT, cause);
    }
}
