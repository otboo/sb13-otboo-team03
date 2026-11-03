package com.otboo.team03.user.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class LastAdminException extends OtbooException {
    public LastAdminException() {
        super(ErrorCode.LAST_ADMIN_ROLE_CHANGE_FORBIDDEN);
    }
}
