package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.dto.response.PrecipitationDto;
import com.otboo.team03.feed.dto.response.TemperatureDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FeedWeatherLookupRepository {

  private final JdbcTemplate jdbcTemplate;

  public boolean existsById(UUID weatherId) {
    // TODO: WeatherRepository 구현 완료 시 JPA 조회로 전환
    Boolean exists = jdbcTemplate.queryForObject(
        "SELECT EXISTS (SELECT 1 FROM weathers WHERE id = ?)",
        Boolean.class,
        weatherId
    );

    return Boolean.TRUE.equals(exists);
  }

  public WeatherSummaryDto findSummaryById(UUID weatherId) {
    // TODO: WeatherRepository 구현 완료 시 JPA 조회로 전환
    return jdbcTemplate.queryForObject("""
                SELECT
                    id,
                    sky_status,
                    precipitation_type,
                    precipitation_amount,
                    precipitation_probability,
                    temperature
                FROM weathers
                WHERE id = ?
                """,
        (rs, rowNum) -> new WeatherSummaryDto(
            rs.getObject("id", UUID.class),
            rs.getString("sky_status"),
            new PrecipitationDto(
                rs.getString("precipitation_type"),
                rs.getDouble("precipitation_amount"),
                rs.getDouble("precipitation_probability")
            ),
            new TemperatureDto(
                rs.getDouble("temperature"),
                null,
                null,
                null
            )
        ),
        weatherId
    );
  }
}
