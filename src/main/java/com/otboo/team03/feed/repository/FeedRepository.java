package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.Feed;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

  List<Feed> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
