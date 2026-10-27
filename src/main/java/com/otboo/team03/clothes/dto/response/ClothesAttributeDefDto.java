package com.otboo.team03.clothes.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClothesAttributeDefDto(
        UUID id, //속성 정의 id
        String name,//속성 정의 이름
        List<String> selectableValues,//선택 가능한 값 목록
        Instant createdAt


) {
}
