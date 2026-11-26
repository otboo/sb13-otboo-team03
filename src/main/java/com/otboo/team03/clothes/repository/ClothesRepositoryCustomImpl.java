package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.UUID;

/** 이름 ASC, ID ASC를 조회 조건과 정렬에 동일하게 적용한다. */
@RequiredArgsConstructor
public class ClothesRepositoryCustomImpl implements ClothesRepositoryCustom {
    private final EntityManager entityManager;

    @Override
    public List<Clothes> findPage(UUID ownerId, ClothesType typeEqual,
                                String cursor, UUID idAfter, int fetchSize) {
        String conditions = " where c.owner.id = :ownerId";
        if (typeEqual != null) conditions += " and c.type = :type";
        // 같은 이름의 의상도 UUID로 구분하여 다음 페이지에서 빠지지 않게 한다.
        if (cursor != null) conditions += " and (c.name > :cursor or (c.name = :cursor and c.id > :idAfter))";
        TypedQuery<Clothes> query = entityManager.createQuery(
                "select c from Clothes c" + conditions + " order by c.name asc, c.id asc", Clothes.class);
        query.setParameter("ownerId", ownerId);
        if (typeEqual != null) query.setParameter("type", typeEqual);
        if (cursor != null) {
            query.setParameter("cursor", cursor);
            query.setParameter("idAfter", idAfter);
        }
        return query.setMaxResults(fetchSize).getResultList();
    }

    @Override
    public long countByFilter(UUID ownerId, ClothesType typeEqual) {
        // 전체 개수에는 페이지 위치를 뜻하는 커서 조건을 적용하지 않는다.
        String jpql = "select count(c) from Clothes c where c.owner.id = :ownerId";
        if (typeEqual != null) jpql += " and c.type = :type";
        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class);
        query.setParameter("ownerId", ownerId);
        if (typeEqual != null) query.setParameter("type", typeEqual);
        return query.getSingleResult();
    }
}
