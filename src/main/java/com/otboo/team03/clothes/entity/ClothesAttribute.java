package com.otboo.team03.clothes.entity;

import com.otboo.team03.global.domain.UpdateEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 특정 의상에 지정한 속성 값. 예: 내 티셔츠의 색상 = 파랑.
 * 속성 이름과 선택 가능한 값 목록은 연결된 ClothesAttributeDefinition에서 조회한다.
 * ID와 생성·수정 시각은 UpdateEntity에서 상속받는다.
 */
@Entity
@Table(
        name = "clothes_attributes",
        // 같은 의상에 같은 속성 정의를 중복 지정하지 못하도록 한다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_clothes_attribute_definition",
                columnNames = {"clothes_id", "definition_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothesAttribute extends UpdateEntity {

    // 이 속성 값이 속한 의상. 하나의 의상에 여러 속성 값을 지정할 수 있다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clothes_id", nullable = false)
    private Clothes clothes;

    // 여러 의상이 공유하는 속성 정의. 예: 색상, 소재, 계절
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "definition_id", nullable = false)
    private ClothesAttributeDefinition definition;

    // 선택한 값. 예: 파랑. 정의의 selectableValues에 포함되는지는 Service에서 검증해야 한다.
    @Column(name = "value", nullable = false, length = 255)
    private String value;

    public ClothesAttribute(
            Clothes clothes,
            ClothesAttributeDefinition definition,
            String value
    ) {
        this.clothes = clothes;
        this.definition = definition;
        this.value = value;
    }
}
