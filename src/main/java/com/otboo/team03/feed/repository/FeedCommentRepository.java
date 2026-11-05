package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.FeedComment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedCommentRepository
    extends JpaRepository<FeedComment, UUID> {
}
