package com.otboo.team03.feed.repository;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.dto.response.AuthorDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedClothes;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FeedBatchQueryRepositoryTest extends PostgresTestSupport {

  @Autowired
  private FeedBatchQueryRepository repository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private ClothesRepository clothesRepository;

  @Autowired
  private FeedClothesRepository feedClothesRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void 빈_ID_집합과_비로그인_사용자는_빈_결과를_반환한다() {
    // given
    Set<UUID> emptyIds = Set.of();
    UUID feedId = UUID.randomUUID();

    // when
    Map<UUID, AuthorDto> authors = repository.findAuthors(emptyIds);
    Map<UUID, WeatherSummaryDto> weathers = repository.findWeathers(emptyIds);
    Map<UUID, FeedBatchQueryRepository.FeedCounts> counts =
        repository.findCounts(emptyIds);
    Set<UUID> likedForEmpty = repository.findLikedFeedIds(
        emptyIds, UUID.randomUUID()
    );
    Set<UUID> likedForGuest = repository.findLikedFeedIds(
        Set.of(feedId), null
    );
    List<FeedBatchQueryRepository.ClothesRow> clothes =
        repository.findClothes(emptyIds);
    Map<UUID, List<ClothesAttributeWithDefDto>> attributes =
        repository.findClothesAttributes(emptyIds);

    // then
    assertTrue(authors.isEmpty());
    assertTrue(weathers.isEmpty());
    assertTrue(counts.isEmpty());
    assertTrue(likedForEmpty.isEmpty());
    assertTrue(likedForGuest.isEmpty());
    assertTrue(clothes.isEmpty());
    assertTrue(attributes.isEmpty());
  }

  @Test
  void 작성자_날씨_좋아요_댓글_집계를_실제_DB에서_일괄_조회한다() {
    // given
    User author = saveUser("피드 작성자");
    User viewer = saveUser("프로필 없는 사용자");
    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update("""
        INSERT INTO profiles (
            user_id, profile_image_url, created_at, updated_at
        ) VALUES (?, ?, ?, ?)
        """,
        author.getId(), "/images/author.png", now, now
    );

    UUID weatherId = insertWeather(now);
    Feed first = feedRepository.saveAndFlush(
        new Feed(author.getId(), weatherId, "첫 번째 피드")
    );
    Feed second = feedRepository.saveAndFlush(
        new Feed(author.getId(), weatherId, "두 번째 피드")
    );

    insertLike(first.getId(), author.getId(), now);
    insertLike(first.getId(), viewer.getId(), now);
    insertComment(first.getId(), author.getId(), "댓글 1", now);
    insertComment(first.getId(), viewer.getId(), "댓글 2", now);

    // when
    Map<UUID, AuthorDto> authors = repository.findAuthors(
        Set.of(author.getId(), viewer.getId())
    );
    Map<UUID, WeatherSummaryDto> weathers = repository.findWeathers(
        Set.of(weatherId)
    );
    Map<UUID, FeedBatchQueryRepository.FeedCounts> counts =
        repository.findCounts(Set.of(first.getId(), second.getId()));
    Set<UUID> liked = repository.findLikedFeedIds(
        Set.of(first.getId(), second.getId()), viewer.getId()
    );
    Set<UUID> notLiked = repository.findLikedFeedIds(
        Set.of(first.getId(), second.getId()), UUID.randomUUID()
    );

    // then
    assertEquals(2, authors.size());
    assertEquals("피드 작성자", authors.get(author.getId()).name());
    assertEquals("/images/author.png",
        authors.get(author.getId()).profileImageUrl());
    assertEquals("프로필 없는 사용자", authors.get(viewer.getId()).name());
    assertNull(authors.get(viewer.getId()).profileImageUrl());

    WeatherSummaryDto weather = weathers.get(weatherId);
    assertNotNull(weather);
    assertEquals("CLEAR", weather.skyStatus());
    assertEquals("NONE", weather.precipitation().type());
    assertEquals(1.5, weather.precipitation().amount());
    assertEquals(40.0, weather.precipitation().probability());
    assertEquals(21.5, weather.temperature().current());

    assertEquals(2, counts.size());
    assertEquals(2L, counts.get(first.getId()).likeCount());
    assertEquals(2, counts.get(first.getId()).commentCount());
    assertEquals(0L, counts.get(second.getId()).likeCount());
    assertEquals(0, counts.get(second.getId()).commentCount());
    assertEquals(Set.of(first.getId()), liked);
    assertTrue(notLiked.isEmpty());
  }

  @Test
  void 의상과_속성을_실제_DB에서_일괄_조회한다() {
    // given
    User author = saveUser("의상 소유자");
    Timestamp now = Timestamp.from(Instant.now());
    UUID weatherId = insertWeather(now);

    Feed feed = feedRepository.saveAndFlush(
        new Feed(author.getId(), weatherId, "의상 목록 조회")
    );

    Clothes shirt = clothesRepository.saveAndFlush(
        new Clothes("셔츠", ClothesType.TOP, "/images/shirt.png", author)
    );
    shirt.updateImageKey("clothes/shirt.png");
    clothesRepository.saveAndFlush(shirt);

    Clothes pants = clothesRepository.saveAndFlush(
        new Clothes("바지", ClothesType.BOTTOM, "/images/pants.png", author)
    );

    feedClothesRepository.saveAndFlush(new FeedClothes(feed, shirt));
    feedClothesRepository.saveAndFlush(new FeedClothes(feed, pants));

    UUID colorDefinitionId = UUID.randomUUID();
    UUID sizeDefinitionId = UUID.randomUUID();
    insertDefinition(colorDefinitionId, "색상", now);
    jdbcTemplate.update("""
    INSERT INTO clothes_attribute_definitions (
        id, name, selectable_values, created_at
    ) VALUES (?, ?, ARRAY['S', 'M', 'L']::text[], ?)
    """,
        sizeDefinitionId,
        "크기",
        now
    );
    jdbcTemplate.update("""
        INSERT INTO clothes_attribute (clothes_id, definition_id, value)
        VALUES (?, ?, ?)
        """, shirt.getId(), colorDefinitionId, "흰색");
    jdbcTemplate.update("""
        INSERT INTO clothes_attribute (clothes_id, definition_id, value)
        VALUES (?, ?, ?)
        """, shirt.getId(), sizeDefinitionId, "M");

    // when
    List<FeedBatchQueryRepository.ClothesRow> rows =
        repository.findClothes(Set.of(feed.getId()));
    Map<UUID, List<ClothesAttributeWithDefDto>> attributes =
        repository.findClothesAttributes(Set.of(shirt.getId(), pants.getId()));

    // then
    assertEquals(2, rows.size());
    FeedBatchQueryRepository.ClothesRow shirtRow = rows.stream()
        .filter(row -> row.clothesId().equals(shirt.getId()))
        .findFirst().orElseThrow();
    FeedBatchQueryRepository.ClothesRow pantsRow = rows.stream()
        .filter(row -> row.clothesId().equals(pants.getId()))
        .findFirst().orElseThrow();

    assertEquals(feed.getId(), shirtRow.feedId());
    assertEquals("셔츠", shirtRow.name());
    assertEquals(ClothesType.TOP, shirtRow.type());
    assertEquals("/images/shirt.png", shirtRow.imageUrl());
    assertEquals("clothes/shirt.png", shirtRow.imageKey());
    assertEquals("바지", pantsRow.name());
    assertEquals(ClothesType.BOTTOM, pantsRow.type());
    assertEquals("/images/pants.png", pantsRow.imageUrl());
    assertNull(pantsRow.imageKey());

    assertEquals(2, attributes.get(shirt.getId()).size());
    assertFalse(attributes.containsKey(pants.getId()));
    ClothesAttributeWithDefDto color = attributes.get(shirt.getId()).stream()
        .filter(item -> item.definitionId().equals(colorDefinitionId))
        .findFirst().orElseThrow();
    ClothesAttributeWithDefDto size = attributes.get(shirt.getId()).stream()
        .filter(item -> item.definitionId().equals(sizeDefinitionId))
        .findFirst().orElseThrow();
    assertEquals("색상", color.definitionName());
    assertEquals(List.of("흰색", "검정"), color.selectableValues());
    assertEquals("흰색", color.value());
    assertEquals("크기", size.definitionName());
    assertEquals("M", size.value());
  }

  private User saveUser(String name) {
    return userRepository.saveAndFlush(
        new User(UUID.randomUUID() + "@example.com", "encodedPassword", name)
    );
  }

  private UUID insertWeather(Timestamp now) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update("""
        INSERT INTO weathers (
            id, latitude, longitude, forecasted_at, forecast_at,
            sky_status, precipitation_type, precipitation_amount,
            precipitation_probability, temperature, humidity, wind_speed,
            created_at, updated_at
        ) VALUES (
            ?, 35.2, 129.1, ?, ?, 'CLEAR', 'NONE', 1.5, 40, 21.5,
            50, 1, ?, ?
        )
        """, id, now, now, now, now);
    return id;
  }

  private void insertLike(UUID feedId, UUID userId, Timestamp now) {
    jdbcTemplate.update("""
        INSERT INTO feed_likes (id, feed_id, user_id, created_at)
        VALUES (?, ?, ?, ?)
        """, UUID.randomUUID(), feedId, userId, now);
  }

  private void insertComment(
      UUID feedId, UUID authorId, String content, Timestamp now
  ) {
    jdbcTemplate.update("""
        INSERT INTO feed_comments (id, feed_id, author_id, content, created_at)
        VALUES (?, ?, ?, ?, ?)
        """, UUID.randomUUID(), feedId, authorId, content, now);
  }

  private void insertDefinition(UUID id, String name, Timestamp now) {
    jdbcTemplate.update("""
        INSERT INTO clothes_attribute_definitions (
            id, name, selectable_values, created_at
        ) VALUES (?, ?, ARRAY['흰색', '검정']::text[], ?)
        """, id, name, now);
  }
}
