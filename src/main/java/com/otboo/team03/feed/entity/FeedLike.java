package com.otboo.team03.feed.entity;

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
    name = "feed_likes",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_feed_likes_feed_user",
        columnNames = {"feed_id", "user_id"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedLike extends CreateEntity {

  @Column(name = "feed_id", nullable = false)
  private UUID feedId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  public FeedLike(UUID feedId, UUID userId) {
    this.feedId = Objects.requireNonNull(feedId);
    this.userId = Objects.requireNonNull(userId);
  }
}
