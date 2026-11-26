package com.otboo.team03.global.exception.cursor;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class InvalidCursorException extends OtbooException {
    public InvalidCursorException() {
        super(ErrorCode.INVALID_INPUT_VALUE);
    }
}
