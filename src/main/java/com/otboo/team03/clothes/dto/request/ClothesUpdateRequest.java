package com.otboo.team03.clothes.dto.request;

import com.otboo.team03.global.domain.ClothesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ClothesUpdateRequest(
        @Size(max = 255)
        String name, //의상 이름

        ClothesType type, //의상 타입
        List<@Valid ClothesAttributeDto> attributes
) {
}
