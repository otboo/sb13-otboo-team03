package com.otboo.team03.weather.entity;

import com.otboo.team03.global.domain.UpdateEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// 기상청 / OpenWeatherMap 단기예보 기반 날씨 데이터 엔티티
@Entity
@Table(name = "weathers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weather extends UpdateEntity {

  @Column(nullable = false)
  private Double latitude;

  @Column(nullable = false)
  private Double longitude;

  // 예보 발표 시각
  @Column(name = "forecasted_at", nullable = false)
  private Instant forecastedAt;

  // 예보 대상 시각
  @Column(name = "forecast_at", nullable = false)
  private Instant forecastAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "sky_status", nullable = false, length = 30)
  private SkyStatus skyStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "precipitation_type", nullable = false, length = 30)
  private PrecipitationType precipitationType;

  // 강수량 (mm) - DB: NUMERIC(7, 2)
  @JdbcTypeCode(SqlTypes.NUMERIC)
  @Column(name = "precipitation_amount", nullable = false)
  private Double precipitationAmount;

  // 강수 확률 (%) - DB: NUMERIC(5, 2)
  @JdbcTypeCode(SqlTypes.NUMERIC)
  @Column(name = "precipitation_probability", nullable = false)
  private Double precipitationProbability;

  // 기온 (℃) - DB: NUMERIC(5, 2)
  @JdbcTypeCode(SqlTypes.NUMERIC)
  @Column(nullable = false)
  private Double temperature;

  // 습도 (%) - DB: NUMERIC(5, 2)
  @JdbcTypeCode(SqlTypes.NUMERIC)
  @Column(nullable = false)
  private Double humidity;

  // 풍속 (m/s) - DB: NUMERIC(6, 2)
  @JdbcTypeCode(SqlTypes.NUMERIC)
  @Column(name = "wind_speed", nullable = false)
  private Double windSpeed;

  @Builder
  public Weather(
      Double latitude,
      Double longitude,
      Instant forecastedAt,
      Instant forecastAt,
      SkyStatus skyStatus,
      PrecipitationType precipitationType,
      Double precipitationAmount,
      Double precipitationProbability,
      Double temperature,
      Double humidity,
      Double windSpeed
  ) {
    this.latitude = Objects.requireNonNull(latitude, "latitude는 필수입니다.");
    this.longitude = Objects.requireNonNull(longitude, "longitude는 필수입니다.");
    this.forecastedAt = Objects.requireNonNull(forecastedAt, "forecastedAt은 필수입니다.");
    this.forecastAt = Objects.requireNonNull(forecastAt, "forecastAt은 필수입니다.");
    this.skyStatus = Objects.requireNonNull(skyStatus, "skyStatus는 필수입니다.");
    this.precipitationType = Objects.requireNonNull(precipitationType, "precipitationType은 필수입니다.");
    this.precipitationAmount = Objects.requireNonNull(precipitationAmount, "precipitationAmount는 필수입니다.");
    this.precipitationProbability = Objects.requireNonNull(precipitationProbability, "precipitationProbability는 필수입니다.");
    this.temperature = Objects.requireNonNull(temperature, "temperature는 필수입니다.");
    this.humidity = Objects.requireNonNull(humidity, "humidity는 필수입니다.");
    this.windSpeed = Objects.requireNonNull(windSpeed, "windSpeed는 필수입니다.");
  }

}
