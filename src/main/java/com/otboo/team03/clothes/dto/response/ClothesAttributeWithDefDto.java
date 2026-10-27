package com.otboo.team03.clothes.dto.response;

import java.util.List;
import java.util.UUID;

public record ClothesAttributeWithDefDto(
        //item
        UUID definitionId,//속성 정의 ID
        String definitionName, //속성 정의 이름
        List<String> selectableValues,//선택 가능한 값 모록
        String value //속성 값

) {
}
