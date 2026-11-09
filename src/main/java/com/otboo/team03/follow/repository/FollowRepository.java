package com.otboo.team03.follow.repository;

import com.otboo.team03.follow.entity.Follow;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
