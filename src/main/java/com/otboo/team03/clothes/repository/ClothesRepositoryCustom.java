package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import java.util.List;
import java.util.UUID;

/** 목록의 커서 정렬 조회와 사전 검색용 무정렬 제한 조회를 구분한다. */
public interface ClothesRepositoryCustom {
    List<Clothes> findPage(UUID ownerId, ClothesType typeEqual, String cursor, UUID idAfter, int fetchSize);
    List<Clothes> findForStyleReferences(UUID ownerId, int fetchSize);
    long countByFilter(UUID ownerId, ClothesType typeEqual);
}
