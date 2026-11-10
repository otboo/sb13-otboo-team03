package com.otboo.team03.feed.repository;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.feed.dto.response.AuthorDto;
import com.otboo.team03.feed.dto.response.PrecipitationDto;
import com.otboo.team03.feed.dto.response.TemperatureDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeedBatchQueryRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public record FeedCounts(long likeCount, int commentCount) {}

  public record ClothesRow(
      UUID feedId,
      UUID clothesId,
      String name,
      ClothesType type,
      String imageUrl,
      String imageKey
  ) {}

  public Map<UUID, AuthorDto> findAuthors(Set<UUID> authorIds) {
    if (authorIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, AuthorDto> result = new HashMap<>();
    jdbcTemplate.query("""
        SELECT u.id AS user_id, u.name, p.profile_image_url
        FROM users u
        LEFT JOIN profiles p ON p.user_id = u.id
        WHERE u.id IN (:ids)
        """, Map.of("ids", authorIds), (RowCallbackHandler) rs -> {
      UUID userId = rs.getObject("user_id", UUID.class);
      result.put(userId, new AuthorDto(
          userId, rs.getString("name"), rs.getString("profile_image_url")
      ));
    });
    return result;
  }

  public Map<UUID, WeatherSummaryDto> findWeathers(Set<UUID> weatherIds) {
    if (weatherIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, WeatherSummaryDto> result = new HashMap<>();
    jdbcTemplate.query("""
        SELECT id, sky_status, precipitation_type,
               precipitation_amount, precipitation_probability, temperature
        FROM weathers
        WHERE id IN (:ids)
        """, Map.of("ids", weatherIds), (RowCallbackHandler) rs -> {
      UUID weatherId = rs.getObject("id", UUID.class);
      result.put(weatherId, new WeatherSummaryDto(
          weatherId,
          rs.getString("sky_status"),
          new PrecipitationDto(
              rs.getString("precipitation_type"),
              rs.getDouble("precipitation_amount"),
              rs.getDouble("precipitation_probability")
          ),
          new TemperatureDto(rs.getDouble("temperature"), null, null, null)
      ));
    });
    return result;
  }

  public Map<UUID, FeedCounts> findCounts(Set<UUID> feedIds) {
    if (feedIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, FeedCounts> result = new HashMap<>();
    jdbcTemplate.query("""
        SELECT f.id AS feed_id,
               COALESCE(l.like_count, 0) AS like_count,
               COALESCE(c.comment_count, 0) AS comment_count
        FROM feeds f
        LEFT JOIN (
          SELECT feed_id, COUNT(*) AS like_count
          FROM feed_likes
          WHERE feed_id IN (:ids)
          GROUP BY feed_id
        ) l ON l.feed_id = f.id
        LEFT JOIN (
          SELECT feed_id, COUNT(*) AS comment_count
          FROM feed_comments
          WHERE feed_id IN (:ids)
          GROUP BY feed_id
        ) c ON c.feed_id = f.id
        WHERE f.id IN (:ids)
        """, Map.of("ids", feedIds), (RowCallbackHandler) rs -> {
      result.put(
          rs.getObject("feed_id", UUID.class),
          new FeedCounts(rs.getLong("like_count"), rs.getInt("comment_count"))
      );
    });
    return result;
  }

  public Set<UUID> findLikedFeedIds(Set<UUID> feedIds, UUID viewerId) {
    if (feedIds.isEmpty() || viewerId == null) {
      return Set.of();
    }

    Set<UUID> result = new HashSet<>();
    jdbcTemplate.query("""
        SELECT feed_id
        FROM feed_likes
        WHERE user_id = :viewerId AND feed_id IN (:ids)
        """, Map.of("viewerId", viewerId, "ids", feedIds),
        (RowCallbackHandler) rs -> result.add(rs.getObject("feed_id", UUID.class)));
    return result;
  }

  public List<ClothesRow> findClothes(Set<UUID> feedIds) {
    if (feedIds.isEmpty()) {
      return List.of();
    }

    List<ClothesRow> result = new ArrayList<>();
    jdbcTemplate.query("""
        SELECT fc.feed_id, c.id AS clothes_id,
               c.name, c.type, c.image_url, c.image_key
        FROM feed_clothes fc
        JOIN clothes c ON c.id = fc.clothes_id
        WHERE fc.feed_id IN (:ids)
        ORDER BY fc.feed_id, c.id
        """, Map.of("ids", feedIds), (RowCallbackHandler) rs -> {
      result.add(new ClothesRow(
          rs.getObject("feed_id", UUID.class),
          rs.getObject("clothes_id", UUID.class),
          rs.getString("name"),
          ClothesType.valueOf(rs.getString("type")),
          rs.getString("image_url"),
          rs.getString("image_key")
      ));
    });
    return result;
  }

  public Map<UUID, List<ClothesAttributeWithDefDto>> findClothesAttributes(
      Set<UUID> clothesIds
  ) {
    if (clothesIds.isEmpty()) {
      return Map.of();
    }

    Map<UUID, List<ClothesAttributeWithDefDto>> result = new HashMap<>();
    jdbcTemplate.query("""
        SELECT ca.clothes_id, ca.definition_id,
               cad.name AS definition_name, cad.selectable_values, ca.value
        FROM clothes_attribute ca
        JOIN clothes_attribute_definitions cad ON cad.id = ca.definition_id
        WHERE ca.clothes_id IN (:ids)
        ORDER BY ca.clothes_id, ca.definition_id
        """, Map.of("ids", clothesIds), (RowCallbackHandler) rs -> {
      UUID clothesId = rs.getObject("clothes_id", UUID.class);
      String[] selectableValues =
          (String[]) rs.getArray("selectable_values").getArray();
      result.computeIfAbsent(clothesId, ignored -> new ArrayList<>()).add(
          new ClothesAttributeWithDefDto(
              rs.getObject("definition_id", UUID.class),
              rs.getString("definition_name"),
              Arrays.asList(selectableValues),
              rs.getString("value")
          )
      );
    });
    return result;
  }
}
