package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.Clothes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ClothesRepository extends JpaRepository<Clothes, UUID > { }
