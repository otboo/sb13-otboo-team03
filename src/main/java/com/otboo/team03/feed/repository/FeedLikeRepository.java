package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.FeedLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedLikeRepository
    extends JpaRepository<FeedLike, UUID> {

  boolean existsByFeedIdAndUserId(UUID feedId, UUID userId);

  void deleteByFeedIdAndUserId(UUID feedId, UUID userId);
}
