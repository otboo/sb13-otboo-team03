package com.otboo.team03.clothes.dto.request;

import com.otboo.team03.clothes.entity.ClothesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ClothesCreateRequest(

        @NotNull(message = "사용자 ID는 필수입니다.")
        UUID ownerId, //소유자 ID

        @NotBlank(message = "의상 이름은 필수입니다.")
        @Size(max = 255)
        String name,//의상 이름

        @NotNull(message = "의상 타입은 필수입니다.")
        ClothesType type, //의상 타입
        /**
         *NotEmpty는 목록이 존재하고 항목이 최소 하나 있어야하는 것.
         *항목의 NotNull은 [nul]처럼 빈 항목을 보내는 요청 거절
         *@Valid : 각 항목의 definitionId, value 검증
         */

        @NotEmpty(message = "의상 속성은 최소 하나 이상 필요합니다.")
        List<@NotNull @Valid ClothesAttributeDto> attributes//의상 속성
) {
}
