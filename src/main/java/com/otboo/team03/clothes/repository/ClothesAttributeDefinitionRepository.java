package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import org.springframework.data.domain.Sort;

public interface ClothesAttributeDefinitionRepository extends JpaRepository<ClothesAttributeDefinition,UUID> {
    // 이름의 부분 일치 검색. Spring Data가 LIKE 특수문자를 이스케이프한다.
    List<ClothesAttributeDefinition> findByNameContainingIgnoreCase(String keyword, Sort sort);
    /** 의상 속성 검증부터 저장 완료까지 정의 변경을 막는다. 다른 의상 등록과는 공유 가능하다. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select d from ClothesAttributeDefinition d where d.id = :id")
    Optional<ClothesAttributeDefinition> findForUsage(@Param("id") UUID id);

    /** 관리자 수정·삭제는 사용 여부 검사 전에 배타 잠금을 잡고 커밋까지 유지한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ClothesAttributeDefinition d where d.id = :id")
    Optional<ClothesAttributeDefinition> findForChange(@Param("id") UUID id);
}
