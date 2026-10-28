package com.otboo.team03.clothes.dto.response;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.entity.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * | 엔티티 | 담는 정보 |
 * |---|---|
 * | `Clothes` | 반팔 티셔츠 |
 * | `ClothesAttribute` | 해당 티셔츠의 색상 값은 파랑 |
 * | `ClothesAttributeDefinition` | 색상, 선택값 `[빨강, 파랑]` |
 * @param id
 * @param ownerId
 * @param name
 * @param imageUrl
 * @param type
 * @param attributes
 */
public record ClothesDto(
        UUID id, //의상 ID
        UUID ownerId, //소유자 ID
        String name, //의상 이름
        String imageUrl, //의상 이미지 URL
        ClothesType type, //의상 타입
        List<ClothesAttributeWithDefDto> attributes //의상 속성


//이렇게 하는 이유는 의상 기본 정보와 속성 값을 서로 다른 엔티티로 나눠나서 그렇다

) {
    public static ClothesDto from(Clothes clothes, List<ClothesAttributeWithDefDto> attributes) {
        return new ClothesDto(
                clothes.getId(),
                clothes.getOwnerId(),
                clothes.getName(),
                clothes.getImageUrl(),
                clothes.getType(),
                attributes

        );
    }
}
