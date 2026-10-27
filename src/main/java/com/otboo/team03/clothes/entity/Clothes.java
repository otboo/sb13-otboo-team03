package com.otboo.team03.clothes.entity;

import com.otboo.team03.clothes.dto.request.ClothesType;
import com.otboo.team03.global.domain.UpdateEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 사용자가 등록한 의상의 기본 정보.
 * 의상별 속성 값은 ClothesAttribute에서 이 엔티티를 참조하여 관리한다.
 * ID와 생성·수정 시각은 UpdateEntity에서 상속받는다.
 */
@Entity
@Table(name = "clothes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Clothes extends UpdateEntity {

    // 사용자가 지정한 의상 이름. 예: 흰색 반팔 티셔츠
    @Column(nullable = false, length = 255)
    private String name;

    // 의상 분류. enum 순서 변경의 영향을 받지 않도록 이름을 문자열로 저장한다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClothesType type;

    // 의상 이미지의 접근 URL. 이미지 파일 자체는 이 테이블에 저장하지 않는다.
    @Column(name = "image_url")
    private String imageUrl;

    // User 엔티티가 준비되면 소유자와 다대일 관계로 연결한다.
    // 현재 소유자 매핑은 비활성화되어 있으며 생성자의 ownerId도 저장되지 않는다.
//    @ManyToOne(fetch = FetchType.LAZY, optional = false)
//    @JoinColumn(name = "owner_id", nullable = false)
//    private User owner; //유저 엔티티 만들어지면 연결

    public Clothes(String name, ClothesType type,
                   String imageUrl, UUID ownerId) {
        this.name = name;
        this.type = type;
        this.imageUrl = imageUrl;
        //this.owner = owner;
    }
}
