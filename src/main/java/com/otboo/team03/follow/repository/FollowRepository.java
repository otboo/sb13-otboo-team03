package com.otboo.team03.follow.repository;

import com.otboo.team03.follow.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

  boolean existsByFollowerIdAndFolloweeId(
      UUID followerId,
      UUID followeeId
  );
}
