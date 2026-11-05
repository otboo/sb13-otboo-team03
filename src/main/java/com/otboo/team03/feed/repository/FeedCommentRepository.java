package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.FeedComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedCommentRepository
    extends JpaRepository<FeedComment, UUID> {
}
