package com.otboo.team03.profile.entity;
import com.otboo.team03.profile.converter.StringListJsonConverter;
import com.otboo.team03.profile.dto.Gender;
import com.otboo.team03.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Profile implements Persistable<UUID> {

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "profile_image_url", length = 2048)
  private String profileImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(length = 10)
  private Gender gender;

  @Column(name = "birth_date")
  private LocalDate birthDate;
  private Double latitude;
  private Double longitude;

  @Column(name = "temperature_sensitivity")
  private Integer temperatureSensitivity;

  @Convert(converter = StringListJsonConverter.class)
  @Column(name = "location_names", columnDefinition = "jsonb")
  private List<String> locationNames = new ArrayList<>();

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Transient
  private boolean isNew = true;

  public Profile(
      User user,
      String profileImageUrl,
      Gender gender,
      LocalDate birthDate,
      Double latitude,
      Double longitude,
      Integer temperatureSensitivity,
      List<String> locationNames
  ) {
    validateTemperatureSensitivity(temperatureSensitivity);
    this.user = user;
    this.userId = user != null ? user.getId() : null;
    this.profileImageUrl = profileImageUrl;
    this.gender = gender;
    this.birthDate = birthDate;
    this.latitude = latitude;
    this.longitude = longitude;
    this.temperatureSensitivity = temperatureSensitivity;
    this.locationNames = locationNames != null ? locationNames : new ArrayList<>();
    this.isNew = true;
  }

  @Override
  public UUID getId() {
    return this.userId;
  }

  @Override
  public boolean isNew() {
    return this.isNew || this.createdAt == null;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  private void validateTemperatureSensitivity(Integer sensitivity) {
    if (sensitivity != null && (sensitivity < 1 || sensitivity > 5)) {
      throw new IllegalArgumentException("온도 민감도는 1에서 5 사이여야 합니다.");
    }
  }

}