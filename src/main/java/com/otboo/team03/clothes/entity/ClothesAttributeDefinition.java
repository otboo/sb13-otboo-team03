package com.otboo.team03.clothes.entity;

import com.otboo.team03.global.domain.UpdateEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 의상에 지정할 수 있는 속성의 정의.
 * 예: 이름 = 색상, 선택 가능한 값 = [빨강, 파랑].
 * 개별 의상에서 선택한 값은 ClothesAttribute에 저장한다.
 * ID와 생성·수정 시각은 UpdateEntity에서 상속받는다.
 */
@Entity
@Table(name = "clothes_attribute_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothesAttributeDefinition extends UpdateEntity {

    // 속성 이름. 예: 색상, 소재, 계절
    @Column(nullable = false, length = 255)
    private String name;

    // 선택 가능한 문자열 목록은 별도 테이블에 저장하고 value_order로 목록 순서를 유지한다.
    @ElementCollection
    @CollectionTable
            (name = "clothes_attribute_selectable_values",
            joinColumns = @JoinColumn(name = "definition_id")
            )
    @OrderColumn(name = "value_order")
    @Column(name = "value", nullable = false, length = 255)
    private List<String> selectableValues = new ArrayList<>();

    public ClothesAttributeDefinition(String name, List<String> selectableValues)
    {
        this.name = name;
        // 전달받은 목록을 복사하여 외부 목록 변경이 엔티티에 직접 반영되지 않도록 한다.
        this.selectableValues.addAll(selectableValues);
    }


}
