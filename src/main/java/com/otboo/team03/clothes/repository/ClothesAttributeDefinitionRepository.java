package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import org.springframework.data.domain.Sort;

public interface ClothesAttributeDefinitionRepository extends JpaRepository<ClothesAttributeDefinition,UUID> {
    // 이름의 부분 일치 검색. Spring Data가 LIKE 특수문자를 이스케이프한다.
    List<ClothesAttributeDefinition> findByNameContainingIgnoreCase(String keyword, Sort sort);
}

