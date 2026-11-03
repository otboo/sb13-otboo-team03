package com.otboo.team03.clothes.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class DuplicateAttributeException extends OtbooException {
    public DuplicateAttributeException() {
        //중복된 속성 정의
        super(ErrorCode.DUPLICATE_ATTRIBUTE);
        //throw new OtbooException(ErrorCode.NOT_FOUND_CLOTHES);
        //-> throw new ClothesNotFoundException(); 이렇게 사용 가능
    }
}
