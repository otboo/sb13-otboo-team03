package com.otboo.team03.feed.entity;

import com.otboo.team03.global.domain.CreateEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "feed_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedComment extends CreateEntity {

  @Column(name = "feed_id", nullable = false)
  private UUID feedId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  public FeedComment(UUID feedId, UUID authorId, String content) {
    this.feedId = Objects.requireNonNull(feedId);
    this.authorId = Objects.requireNonNull(authorId);
    this.content = Objects.requireNonNull(content);
  }
}
