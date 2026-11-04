package com.otboo.team03.feed.mapper;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedClothes;
import com.otboo.team03.feed.repository.FeedClothesRepository;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FeedDtoAssemblerTest extends com.otboo.team03.support.PostgresTestSupport {

  @Autowired
  private FeedDtoAssembler assembler;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ClothesRepository clothesRepository;

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private FeedClothesRepository feedClothesRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  private com.otboo.team03.global.storage.ImageStorage imageStorage;

  @Autowired
  private jakarta.persistence.EntityManager entityManager;

  @Test
  void 피드의_작성자_날씨_의상_속성을_응답으로_변환한다() {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "테스트 사용자"
        )
    );

    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update("""
                INSERT INTO profiles (
                    user_id, profile_image_url,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?)
                """,
        author.getId(),
        "/images/profile.png",
        now,
        now
    );

    UUID weatherId = UUID.randomUUID();

    jdbcTemplate.update("""
                INSERT INTO weathers (
                    id, latitude, longitude,
                    forecasted_at, forecast_at,
                    sky_status, precipitation_type,
                    precipitation_amount, precipitation_probability,
                    temperature, humidity, wind_speed,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
        weatherId,
        35.2,
        129.1,
        now,
        now,
        "CLEAR",
        "NONE",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.valueOf(20),
        BigDecimal.valueOf(50),
        BigDecimal.ONE,
        now,
        now
    );

    Clothes clothes = clothesRepository.saveAndFlush(
        new Clothes(
            "흰색 티셔츠",
            ClothesType.TOP,
            "/images/shirt.png",
            author
        )
    );

    UUID definitionId = UUID.randomUUID();

    jdbcTemplate.update("""
                INSERT INTO clothes_attribute_definitions (
                    id, name, selectable_values, created_at
                ) VALUES (?, ?, ARRAY['흰색', '검정']::text[], ?)
                """,
        definitionId,
        "색상",
        now
    );

    jdbcTemplate.update("""
                INSERT INTO clothes_attribute (
                    clothes_id, definition_id, value
                ) VALUES (?, ?, ?)
                """,
        clothes.getId(),
        definitionId,
        "흰색"
    );

    Feed feed = feedRepository.saveAndFlush(
        new Feed(author.getId(), weatherId, "오늘의 코디")
    );

    feedClothesRepository.saveAndFlush(
        new FeedClothes(feed, clothes)
    );

    // when
    FeedDto result = assembler.toDto(feed, null);

    // then
    assertAll(
        () -> assertEquals(feed.getId(), result.id()),
        () -> assertNotNull(result.createdAt()),
        () -> assertNotNull(result.updatedAt()),
        () -> assertEquals("오늘의 코디", result.content()),
        () -> assertEquals(author.getId(), result.author().userId()),
        () -> assertEquals("테스트 사용자", result.author().name()),
        () -> assertEquals(
            "/images/profile.png",
            result.author().profileImageUrl()
        ),
        () -> assertEquals(weatherId, result.weather().weatherId()),
        () -> assertEquals("CLEAR", result.weather().skyStatus()),
        () -> assertEquals(
            "NONE",
            result.weather().precipitation().type()
        ),
        () -> assertEquals(
            20.0,
            result.weather().temperature().current()
        ),
        () -> assertEquals(1, result.ootds().size()),
        () -> assertEquals(
            clothes.getId(),
            result.ootds().get(0).clothesId()
        ),
        () -> assertEquals(
            ClothesType.TOP,
            result.ootds().get(0).type()
        ),
        () -> assertEquals(
            "/images/shirt.png",
            result.ootds().get(0).imageUrl()
        ),
        () -> assertEquals(
            definitionId,
            result.ootds().get(0).attributes()
                .get(0).definitionId()
        ),
        () -> assertEquals(
            "흰색",
            result.ootds().get(0).attributes()
                .get(0).value()
        ),
        () -> assertEquals(0L, result.likeCount()),
        () -> assertEquals(0, result.commentCount()),
        () -> assertFalse(result.likedByMe())
    );

    Clothes withoutImage = clothesRepository.saveAndFlush(new Clothes("이미지 없음", ClothesType.TOP, null, author));
    feedClothesRepository.saveAndFlush(new FeedClothes(feed, withoutImage));
    assertNull(assembler.toDto(feed, null).ootds().stream()
        .filter(item -> item.clothesId().equals(withoutImage.getId())).findFirst().orElseThrow().imageUrl());
    org.mockito.Mockito.verifyNoInteractions(imageStorage);
    entityManager.flush();
    entityManager.clear();
    clothesRepository.deleteById(withoutImage.getId());
    clothesRepository.flush();
    entityManager.clear();

    // 영구 key가 있으면 기존 URL보다 우선하며, 응답마다 URL을 다시 생성한다.
    clothes.updateImageKey("clothes/feed.png");
    clothesRepository.saveAndFlush(clothes);
    org.mockito.Mockito.when(imageStorage.getAccessUrl("clothes/feed.png"))
        .thenReturn("https://example.com/first-signature", "https://example.com/refreshed-signature");
    assertEquals("https://example.com/first-signature", assembler.toDto(feed, author.getId()).ootds().get(0).imageUrl());
    assertEquals("https://example.com/refreshed-signature", assembler.toDto(feed, author.getId()).ootds().get(0).imageUrl());
    assertEquals("clothes/feed.png", clothes.getImageKey());
    assertEquals("/images/shirt.png", clothes.getImageUrl());
    org.mockito.Mockito.verify(imageStorage, org.mockito.Mockito.times(2)).getAccessUrl("clothes/feed.png");

    // 실제 FK cascade 이후 새 영속성 컨텍스트에서도 본문은 보존되고 의상만 빠진다.
    UUID feedId = feed.getId();
    entityManager.flush();
    entityManager.clear();
    clothesRepository.deleteById(clothes.getId());
    clothesRepository.flush();
    entityManager.clear();
    Feed retained = feedRepository.findById(feedId).orElseThrow();
    FeedDto afterDeletion = assembler.toDto(retained, null);
    assertEquals("오늘의 코디", afterDeletion.content());
    assertTrue(afterDeletion.ootds().isEmpty());
    assertFalse(clothesRepository.existsById(clothes.getId()));
    assertEquals(0, jdbcTemplate.queryForObject(
        "SELECT count(*) FROM feed_clothes WHERE feed_id = ?", Integer.class, feedId));
  }

}
