package com.otboo.team03.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 의상 생성·수정 요청에서 클라이언트가 전달하는 속성 항목.
 * 정의 ID와 선택한 값만 전달한다. 예: definitionId=색상 정의 ID, value=파랑.
 * 정의 이름과 선택지는 서버가 정의 데이터에서 확인하므로 요청에 포함하지 않는다.
 * 수정 요청 DTO에도 사용되지만 현재 서비스의 속성 수정 처리는 미구현이다.
 */
public record ClothesAttributeDto(

        @NotNull(message = "속성 정의 ID는 필수입니다.")
        UUID definitionId, //속성 정의 ID

        @NotBlank(message = "속성 값은 필수입니다.")
        @Size(max = 255)
        String value //속성 값
) {
}
