package com.otboo.team03.clothes.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class InvalidClothesAttributeValueException extends OtbooException {
    public InvalidClothesAttributeValueException() {
        //허용되지 않는 속성
        super(ErrorCode.INVALID_CLOTHES_ATTRIBUTE_VALUE);
        //throw new OtbooException(ErrorCode.NOT_FOUND_CLOTHES);
        //-> throw new ClothesNotFoundException(); 이렇게 사용 가능
    }}
