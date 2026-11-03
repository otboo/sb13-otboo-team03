package com.otboo.team03.clothes.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * 특정 의상이 선택한 속성값에 해당 정의의 이름과 선택지를 합친 응답.
 * 예: definitionName=색상, selectableValues=[빨강, 파랑], value=파랑.
 * ClothesDto.attributes 및 피드 도메인의 OotdDto.attributes에서 사용한다.
 * definitionId는 ClothesAttributeDefinition의 ID이며 의상 ID나 별도 속성 행 ID가 아니다.
 * 같은 정의라도 의상마다 value가 다를 수 있다.
 */
public record ClothesAttributeWithDefDto(
        UUID definitionId,//속성 정의 ID
        String definitionName, //속성 정의 이름
        List<String> selectableValues,//선택 가능한 값 목록
        String value //속성 값

) {
}
