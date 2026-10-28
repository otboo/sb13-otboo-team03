package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.ClothesAttributeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClothesAttributeDefinitionRepository extends JpaRepository<ClothesAttributeDefinition,UUID> {
}
