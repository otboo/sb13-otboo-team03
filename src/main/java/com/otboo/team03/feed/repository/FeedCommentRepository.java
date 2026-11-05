package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.FeedComment;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedCommentRepository
    extends JpaRepository<FeedComment, UUID> {

  List<FeedComment> findByFeedIdOrderByCreatedAtDescIdDesc(
      UUID feedId,
      Pageable pageable
  );

  long countByFeedId(UUID feedId);


  @Query(value = """
    SELECT fc.*
    FROM feed_comments fc
    WHERE fc.feed_id = :feedId
      AND (
          fc.created_at < :cursor
          OR (
              fc.created_at = :cursor
              AND fc.id < :idAfter
          )
      )
    ORDER BY fc.created_at DESC, fc.id DESC
    LIMIT :pageSize
    """, nativeQuery = true)
  List<FeedComment> findNextPage(
      @Param("feedId") UUID feedId,
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      @Param("pageSize") int pageSize
  );
}
