package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.Clothes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClothesRepository extends JpaRepository<Clothes, UUID >, ClothesRepositoryCustom {
    // owner 객체의 id로 해당 사용자의 의상 목록을 조회한다.
    List<Clothes> findAllByOwner_Id(UUID ownerId);


}
