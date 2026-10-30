package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import java.util.List;
import java.util.UUID;

/** Spring Data 메서드 이름으로 표현하기 어려운 커서 조건을 별도 구현한다. */
public interface ClothesRepositoryCustom {
    List<Clothes> findPage(UUID ownerId, ClothesType typeEqual, String cursor, UUID idAfter, int fetchSize);
    long countByFilter(UUID ownerId, ClothesType typeEqual);
}
