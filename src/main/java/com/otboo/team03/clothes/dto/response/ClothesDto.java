package com.otboo.team03.clothes.dto.response;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;

import java.util.List;
import java.util.UUID;

/**
 * 의상 생성·단건 조회·목록 조회에서 반환하는 의상 한 벌의 응답.
 * id는 의상 ID, ownerId는 소유 사용자 ID이며 속성 정의 ID와 구분한다.
 * attributes에는 해당 의상의 선택값과 정의 정보를 ClothesAttributeWithDefDto로 담는다.
 * 예: name=흰 티셔츠, attributes=[색상 정의 + 선택값 파랑].
 * 피드 응답은 별도 OotdDto를 사용하고 속성 항목 DTO만 공유한다.
 * from()은 전달받은 속성 목록을 조립하며 DB 조회를 수행하지 않는다.
 */
public record ClothesDto(
        UUID id, //의상 ID
        UUID ownerId, //소유자 ID
        String name, //의상 이름
        String imageUrl, //의상 이미지 URL
        ClothesType type, //의상 타입
        List<ClothesAttributeWithDefDto> attributes //의상 속성




) {
    public static ClothesDto from(Clothes clothes, List<ClothesAttributeWithDefDto> attributes) {
        return new ClothesDto(
                clothes.getId(),
                clothes.getOwner().getId(),
                clothes.getName(),
                clothes.getImageUrl(),
                clothes.getType(),
                attributes

        );
    }
}
