package com.otboo.team03.notification.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class NotificationNotFoundException extends OtbooException {

    public NotificationNotFoundException() {
        super(ErrorCode.NOT_FOUND_NOTIFICATION);
    }
}
