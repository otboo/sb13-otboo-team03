package com.otboo.team03.feed.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "weathers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedWeatherFilter {

  @Id
  @Column(name = "id")
  private UUID id;

  @Column(name = "sky_status", nullable = false)
  private String skyStatus;

  @Column(name = "precipitation_type", nullable = false)
  private String precipitationType;
}
