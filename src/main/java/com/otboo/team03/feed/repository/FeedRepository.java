package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.Feed;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

  List<Feed> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);

  List<Feed> findAllByOrderByCreatedAtAscIdAsc(Pageable pageable);

  @Query(value = """
      SELECT *
      FROM feeds
      WHERE created_at < :cursor
         OR (created_at = :cursor AND id < :idAfter)
      ORDER BY created_at DESC, id DESC
      LIMIT :pageSize
      """, nativeQuery = true)
  List<Feed> findNextPage(
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      @Param("pageSize") int pageSize
  );

  @Query(value = """
      SELECT *
      FROM feeds
      WHERE created_at > :cursor
         OR (created_at = :cursor AND id > :idAfter)
      ORDER BY created_at ASC, id ASC
      LIMIT :pageSize
      """, nativeQuery = true)
  List<Feed> findNextPageAscending(
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      @Param("pageSize") int pageSize
  );

  @Query(value = """
      SELECT f.*
      FROM feeds f
      LEFT JOIN feed_likes fl ON fl.feed_id = f.id
      GROUP BY f.id
      ORDER BY COUNT(fl.id) DESC, f.id DESC
      LIMIT :pageSize
      """, nativeQuery = true)
  List<Feed> findFirstPageByLikeCountDesc(
      @Param("pageSize") int pageSize
  );

  @Query(value = """
    SELECT f.*
    FROM feeds f
    LEFT JOIN feed_likes fl ON fl.feed_id = f.id
    GROUP BY f.id
    HAVING COUNT(fl.id) < :cursorCount
       OR (
           COUNT(fl.id) = :cursorCount
           AND f.id < :idAfter
       )
    ORDER BY COUNT(fl.id) DESC, f.id DESC
    LIMIT :pageSize
    """, nativeQuery = true)
  List<Feed> findNextPageByLikeCountDesc(
      @Param("cursorCount") long cursorCount,
      @Param("idAfter") UUID idAfter,
      @Param("pageSize") int pageSize
  );

  @Query(value = """
    SELECT f.*
    FROM feeds f
    LEFT JOIN feed_likes fl ON fl.feed_id = f.id
    GROUP BY f.id
    ORDER BY COUNT(fl.id) ASC, f.id ASC
    LIMIT :pageSize
    """, nativeQuery = true)
  List<Feed> findFirstPageByLikeCountAsc(
      @Param("pageSize") int pageSize
  );

  @Query(value = """
    SELECT f.*
    FROM feeds f
    LEFT JOIN feed_likes fl ON fl.feed_id = f.id
    GROUP BY f.id
    HAVING COUNT(fl.id) > :cursorCount
       OR (
           COUNT(fl.id) = :cursorCount
           AND f.id > :idAfter
       )
    ORDER BY COUNT(fl.id) ASC, f.id ASC
    LIMIT :pageSize
    """, nativeQuery = true)
  List<Feed> findNextPageByLikeCountAsc(
      @Param("cursorCount") long cursorCount,
      @Param("idAfter") UUID idAfter,
      @Param("pageSize") int pageSize
  );
}
