package com.otboo.team03.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ClothesAttributeDto(

        @NotNull(message = "속성 정의 ID는 필수입니다.")
        UUID definitionId, //속성 정의 ID

        @NotBlank(message = "속성 값은 필수입니다.")
        @Size(max = 255)
        String value //속성 값
) {
}