package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.ClothesAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClothesAttributeRepository
        extends JpaRepository<ClothesAttribute, ClothesAttribute.Key> {
}
