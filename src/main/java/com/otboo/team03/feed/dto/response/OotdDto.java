package com.otboo.team03.feed.dto.response;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.dto.response.ClothesDto;
import com.otboo.team03.clothes.entity.ClothesType;

import java.util.List;
import java.util.UUID;

public record OotdDto(
    UUID clothesId,
    String name,
    String imageUrl,
    ClothesType type,
    List<ClothesAttributeWithDefDto> attributes
) {

    public static OotdDto fromClothesDto(ClothesDto dto) {
        return new OotdDto(dto.id(), dto.name(), dto.imageUrl(), dto.type(), dto.attributes());
    }
}
