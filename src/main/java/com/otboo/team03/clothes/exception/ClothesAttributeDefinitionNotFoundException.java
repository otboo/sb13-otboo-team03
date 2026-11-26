package com.otboo.team03.clothes.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;

public class ClothesAttributeDefinitionNotFoundException extends OtbooException {
    public ClothesAttributeDefinitionNotFoundException() {
        super(ErrorCode.NOT_FOUND_ATTRIBUTE_DEFINITION);
        //throw new OtbooException(ErrorCode.NOT_FOUND_CLOTHES);
        //-> throw new ClothesNotFoundException(); 이렇게 사용 가능
    }}
