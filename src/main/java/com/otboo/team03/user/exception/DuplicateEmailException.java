package com.otboo.team03.user.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class DuplicateEmailException extends OtbooException {
    public DuplicateEmailException() {
        super(ErrorCode.DUPLICATE_EMAIL);
    }
}
