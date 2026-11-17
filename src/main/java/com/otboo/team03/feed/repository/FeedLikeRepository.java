package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.FeedLike;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedLikeRepository
    extends JpaRepository<FeedLike, UUID> {

  boolean existsByFeedIdAndUserId(UUID feedId, UUID userId);

  void deleteByFeedIdAndUserId(UUID feedId, UUID userId);

  @Modifying
  @Query(value = """
      INSERT INTO feed_likes (
          id, feed_id, user_id, created_at
      )
      VALUES (
          :id, :feedId, :userId, :createdAt
      )
      ON CONFLICT (feed_id, user_id) DO NOTHING
      """, nativeQuery = true)
  int insertIfAbsent(
      @Param("id") UUID id,
      @Param("feedId") UUID feedId,
      @Param("userId") UUID userId,
      @Param("createdAt") Instant createdAt
  );
}
