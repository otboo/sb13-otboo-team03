package com.otboo.team03.feed.entity;

import com.otboo.team03.clothes.entity.Clothes;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "feed_clothes")
@IdClass(FeedClothes.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedClothes {

  @Id
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "feed_id", nullable = false)
  private Feed feed;

  @Id
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "clothes_id", nullable = false)
  private Clothes clothes;

  public FeedClothes(Feed feed, Clothes clothes) {
    this.feed = Objects.requireNonNull(feed);
    this.clothes = Objects.requireNonNull(clothes);
  }

  @NoArgsConstructor
  @AllArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {
    private UUID feed;
    private UUID clothes;
  }
}
