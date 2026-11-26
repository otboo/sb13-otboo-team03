package com.otboo.team03.clothes.dto.request;

import com.otboo.team03.clothes.entity.ClothesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** attributes 생략/null은 유지, 빈 목록은 전체 제거, 목록 전달은 전체 교체한다. */
public record ClothesUpdateRequest(
        @Size(max = 255)
        String name, //의상 이름

        ClothesType type, //의상 타입
        List<@NotNull @Valid ClothesAttributeDto> attributes
) {
}
