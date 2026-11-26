package com.otboo.team03.profile.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class ProfileNotFoundException extends OtbooException {
  public ProfileNotFoundException() {
    super(ErrorCode.NOT_FOUND_PROFILE);
  }
}
