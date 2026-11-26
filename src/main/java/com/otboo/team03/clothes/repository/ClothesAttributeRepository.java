package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.ClothesAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Collection;
import java.util.UUID;

public interface ClothesAttributeRepository
        extends JpaRepository<ClothesAttribute, ClothesAttribute.Key> {
    // DTO 변환에 필요한 정의도 함께 조회해 속성마다 추가 SELECT가 발생하지 않도록 한다.
    @EntityGraph(attributePaths = "definition")
    List<ClothesAttribute> findAllByClothes_Id(UUID clothesId);

    // 목록에 포함된 의상들의 속성과 정의를 IN 조건으로 한 번에 조회한다.
    @EntityGraph(attributePaths = "definition")
    List<ClothesAttribute> findAllByClothes_IdIn(Collection<UUID> clothesIds);

    // 공유 정의를 삭제해도 되는지 확인한다. 하나라도 참조 중이면 정의 삭제를 거부한다.
    // 의상 자체가 아니라 clothes_attribute의 참조 존재 여부를 조회한다.
    boolean existsByDefinition_Id(UUID definitionId);

    // 정의 수정 시 제거할 선택값이 실제 의상에 남아 있는지 확인한다. 빈 values 목록은 호출 전에 제외한다.
    boolean existsByDefinition_IdAndValueIn(UUID definitionId, Collection<String> values);

}
