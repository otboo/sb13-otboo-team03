package com.otboo.team03.follow.repository;

import com.otboo.team03.follow.entity.Follow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

  boolean existsByFollowerIdAndFolloweeId(
      UUID followerId,
      UUID followeeId
  );

  List<Follow> findByFolloweeIdOrderByCreatedAtDescIdDesc(
      UUID followeeId,
      Pageable pageable
  );

  long countByFolloweeId(UUID followeeId);

  @Query("""
      SELECT f
      FROM Follow f, User u
      WHERE f.followerId = u.id
        AND f.followeeId = :followeeId
        AND LOWER(u.name) LIKE LOWER(CONCAT('%', :nameLike, '%'))
      ORDER BY f.createdAt DESC, f.id DESC
      """)
  List<Follow> findFollowersByNameLike(
      @Param("followeeId") UUID followeeId,
      @Param("nameLike") String nameLike,
      Pageable pageable
  );

  @Query("""
      SELECT COUNT(f)
      FROM Follow f, User u
      WHERE f.followerId = u.id
        AND f.followeeId = :followeeId
        AND LOWER(u.name) LIKE LOWER(CONCAT('%', :nameLike, '%'))
      """)
  long countFollowersByNameLike(
      @Param("followeeId") UUID followeeId,
      @Param("nameLike") String nameLike
  );
}
