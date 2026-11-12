package com.otboo.team03.follow.repository;

import com.otboo.team03.follow.entity.Follow;
import java.time.Instant;
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

  List<Follow> findByFollowerIdOrderByCreatedAtDescIdDesc(
      UUID followerId,
      Pageable pageable
  );

  long countByFollowerId(UUID followerId);


  @Query("""
    SELECT f
    FROM Follow f, User u
    WHERE f.followeeId = u.id
      AND f.followerId = :followerId
      AND LOWER(u.name) LIKE LOWER(CONCAT('%', :nameLike, '%'))
    ORDER BY f.createdAt DESC, f.id DESC
    """)
  List<Follow> findFollowingsByNameLike(
      @Param("followerId") UUID followerId,
      @Param("nameLike") String nameLike,
      Pageable pageable
  );

  @Query("""
    SELECT COUNT(f)
    FROM Follow f, User u
    WHERE f.followeeId = u.id
      AND f.followerId = :followerId
      AND LOWER(u.name) LIKE LOWER(CONCAT('%', :nameLike, '%'))
    """)
  long countFollowingsByNameLike(
      @Param("followerId") UUID followerId,
      @Param("nameLike") String nameLike
  );

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

  @Query(value = """
      SELECT f.*
      FROM follows f
      WHERE f.followee_id = :followeeId
        AND (
          f.created_at < :cursor
          OR (f.created_at = :cursor AND f.id < :idAfter)
        )
      ORDER BY f.created_at DESC, f.id DESC
      """, nativeQuery = true)
  List<Follow> findNextPage(
      @Param("followeeId") UUID followeeId,
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable
  );

  @Query(value = """
      SELECT f.*
      FROM follows f
      JOIN users u ON f.follower_id = u.id
      WHERE f.followee_id = :followeeId
        AND LOWER(u.name) LIKE LOWER(CONCAT('%', :nameLike, '%'))
        AND (
          f.created_at < :cursor
          OR (f.created_at = :cursor AND f.id < :idAfter)
        )
      ORDER BY f.created_at DESC, f.id DESC
      """, nativeQuery = true)
  List<Follow> findNextPageByNameLike(
      @Param("followeeId") UUID followeeId,
      @Param("nameLike") String nameLike,
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable
  );

  @Query(value = """
    SELECT f.*
    FROM follows f
    WHERE f.follower_id = :followerId
      AND (
        f.created_at < :cursor
        OR (f.created_at = :cursor AND f.id < :idAfter)
      )
    ORDER BY f.created_at DESC, f.id DESC
    """, nativeQuery = true)
  List<Follow> findNextFollowingsPage(
      @Param("followerId") UUID followerId,
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable
  );
}
