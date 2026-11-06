package com.otboo.team03.follow.entity;

import com.otboo.team03.global.domain.CreateEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "follows",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_follows_follower_followee",
        columnNames = {"follower_id", "followee_id"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Follow extends CreateEntity {

  @Column(name = "follower_id", nullable = false)
  private UUID followerId;

  @Column(name = "followee_id", nullable = false)
  private UUID followeeId;

  public Follow(UUID followerId, UUID followeeId) {
    this.followerId = Objects.requireNonNull(followerId);
    this.followeeId = Objects.requireNonNull(followeeId);
  }
}
