package com.otboo.team03.clothes.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 관리자용 공유 속성 정의 수정 요청. PATCH에서 생략/null은 기존 값을 유지한다.
 * 선택지 목록은 전체 교체하며 최소 한 개가 필요하다. 사용 중인 값 제거는 Service에서 거부한다.
 * 특정 의상의 선택값 수정은 ClothesUpdateRequest를 사용한다.
 */
public record ClothesAttributeDefUpdateRequest(
        @Size(max = 255) String name,
        @Size(min = 1) List<@NotBlank @Size(max = 255) String> selectableValues
) {}
