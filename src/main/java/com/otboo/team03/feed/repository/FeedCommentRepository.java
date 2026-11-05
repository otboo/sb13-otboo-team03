package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.FeedComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedCommentRepository
    extends JpaRepository<FeedComment, UUID> {

  List<FeedComment> findByFeedIdOrderByCreatedAtDescIdDesc(
      UUID feedId,
      Pageable pageable
  );

  long countByFeedId(UUID feedId);
}
