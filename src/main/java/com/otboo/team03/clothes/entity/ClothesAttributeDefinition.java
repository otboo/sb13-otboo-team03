package com.otboo.team03.clothes.entity;

import com.otboo.team03.global.domain.CreateEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * 의상에 지정할 수 있는 속성의 정의.
 * 예: 이름 = 색상, 선택 가능한 값 = [빨강, 파랑].
 * 개별 의상에서 선택한 값은 ClothesAttribute에 저장한다.
 * ID와 생성 시각은 CreateEntity에서 상속받는다.
 */
@Entity
@Table(name = "clothes_attribute_definitions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClothesAttributeDefinition extends CreateEntity {

    // 속성 이름. 예: 색상, 소재, 계절
    @Column(nullable = false, length = 255)
    private String name;

    // 기존 스키마의 PostgreSQL TEXT[] 컬럼에 목록을 저장한다.
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "selectable_values", nullable = false, columnDefinition = "text[]")
    private List<String> selectableValues = new ArrayList<>();

    public ClothesAttributeDefinition(String name, List<String> selectableValues)
    {
        this.name = name;
        // 전달받은 목록을 복사하여 외부 목록 변경이 엔티티에 직접 반영되지 않도록 한다.
        this.selectableValues.addAll(selectableValues);
    }
    /**
     * 공유 정의의 이름과 선택지를 변경한다. 의상별 선택값은 변경하지 않는다.
     * Service에서 사용 중인 값 제거 여부를 먼저 검사한다. 생략 필드는 유지하고 목록은 복사한다.
     */
    public void update(String name, List<String> selectableValues) {
        if (name != null) this.name = name;
        if (selectableValues != null) this.selectableValues = new ArrayList<>(selectableValues);
    }
}
