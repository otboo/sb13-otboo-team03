package com.otboo.team03.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ClothesAttributeDefCreateRequest(
        @NotBlank
        @Size(max = 255)
        String name,//속성 정의 이름

        @NotNull
        List<@NotBlank String> selectableValues//선택 가능한 값 목록
) {
}
