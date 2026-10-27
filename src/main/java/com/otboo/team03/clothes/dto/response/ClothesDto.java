package com.otboo.team03.clothes.dto.response;

import com.otboo.team03.clothes.entity.ClothesType;

import java.util.List;
import java.util.UUID;

public record ClothesDto(
        UUID id, //의상 ID
        UUID ownerId, //소유자 ID
        String name, //의상 이름
        String imageUrl, //의상 이미지 URL
        ClothesType type, //의상 타입
        List<ClothesAttributeWithDefDto> attributes //의상 속성



) {
}
