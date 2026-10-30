package com.otboo.team03.clothes.dto.response;

import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 속성 정의에 생성 시각까지 포함하는 응답 형태.
 * 예: 색상, 선택지 [빨강, 파랑], 정의 생성 시각.
 * 속성 정의 조회 API의 응답 항목으로 사용한다. createdAt은 DB에 저장된 정의 생성 시각이다.
 * id는 의상 ID가 아닌 속성 정의 ID이며, 개별 의상의 선택값(value)은 포함하지 않는다.
 */
public record ClothesAttributeDefDto(
        UUID id, //속성 정의 id
        String name,//속성 정의 이름
        List<String> selectableValues,//선택 가능한 값 목록
        Instant createdAt


) {
    public static ClothesAttributeDefDto from(ClothesAttributeDefinition definition) {
        return new ClothesAttributeDefDto(
                definition.getId(),
                definition.getName(),
                List.copyOf(definition.getSelectableValues()),
                definition.getCreatedAt()
        );
    }
}
