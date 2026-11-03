package com.otboo.team03.clothes.repository;

import com.otboo.team03.clothes.entity.Clothes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface ClothesRepository extends JpaRepository<Clothes, UUID >, ClothesRepositoryCustom {
    // owner 객체의 id로 해당 사용자의 의상 목록을 조회한다.
    List<Clothes> findAllByOwner_Id(UUID ownerId);

    /**
     * 수정·삭제는 같은 의상 행을 커밋까지 잠근다.
     * 대기한 요청은 최신 imageKey를 읽어 삭제된 이전 key를 다시 저장하지 않는다.
     * 일반 조회에는 이 잠금을 사용하지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Clothes c where c.id = :id")
    Optional<Clothes> findForChange(@Param("id") UUID id);
}
