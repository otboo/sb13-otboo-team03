package com.otboo.team03.feed.entity;

import com.otboo.team03.global.domain.UpdateEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "feeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feed extends UpdateEntity {

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "weather_id", nullable = false)
  private UUID weatherId;

  @Column(name = "content", nullable = false, columnDefinition = "TEXT")
  private String content;

  public Feed(UUID authorId, UUID weatherId, String content) {
    this.authorId = Objects.requireNonNull(authorId);
    this.weatherId = Objects.requireNonNull(weatherId);
    this.content = Objects.requireNonNull(content);
  }

  public void updateContent(String content) {
    this.content = Objects.requireNonNull(content);
  }
}
