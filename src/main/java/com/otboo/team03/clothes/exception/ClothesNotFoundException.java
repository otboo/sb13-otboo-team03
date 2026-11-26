package com.otboo.team03.clothes.exception;

import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public class ClothesNotFoundException extends OtbooException {
    public ClothesNotFoundException() {
        super(ErrorCode.NOT_FOUND_CLOTHES);
        //throw new OtbooException(ErrorCode.NOT_FOUND_CLOTHES);
        //-> throw new ClothesNotFoundException(); 이렇게 사용 가능
    }
}
