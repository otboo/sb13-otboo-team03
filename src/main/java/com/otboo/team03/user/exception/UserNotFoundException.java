package com.otboo.team03.user.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class UserNotFoundException extends OtbooException {
    public UserNotFoundException() {
        super(ErrorCode.NOT_FOUND_USER);
    }

}
