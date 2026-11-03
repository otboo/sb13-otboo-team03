package com.otboo.team03.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 관리자가 공유 속성의 이름과 선택지를 등록할 때 사용하는 요청. 개별 의상의 선택값은 포함하지 않는다. */
public record ClothesAttributeDefCreateRequest(
        @NotBlank
        @Size(max = 255)
        String name,//속성 정의 이름

        @NotEmpty
        List<@NotBlank @Size(max = 255) String> selectableValues//선택 가능한 값 목록
) {
}
