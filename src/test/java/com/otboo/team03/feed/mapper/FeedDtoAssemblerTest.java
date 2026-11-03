package com.otboo.team03.feed.mapper;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedClothes;
import com.otboo.team03.feed.repository.FeedClothesRepository;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class FeedDtoAssemblerTest {

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

  @MockitoBean
  private ImageStorage imageStorage;

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
  }

  @Test
  void S3_이미지_키가_있으면_서명_URL을_반환한다() {
    // given
    String imageKey = "clothes/test-shirt.png";
    String signedUrl = "https://signed.example.com/test-shirt.png";

    FeedFixture fixture = createFeedFixture(
        "/images/old-shirt.png",
        imageKey
    );

    when(imageStorage.getAccessUrl(imageKey))
        .thenReturn(signedUrl);

    // when
    FeedDto result = assembler.toDto(fixture.feed(), null);

    // then
    assertAll(
        () -> assertEquals(1, result.ootds().size()),
        () -> assertEquals(
            fixture.clothes().getId(),
            result.ootds().get(0).clothesId()
        ),
        () -> assertEquals(
            signedUrl,
            result.ootds().get(0).imageUrl()
        )
    );

    verify(imageStorage).getAccessUrl(imageKey);
  }

  @Test
  void 이미지_키가_없으면_기존_URL을_반환한다() {
    // given
    String imageUrl = "/images/legacy-shirt.png";

    FeedFixture fixture = createFeedFixture(
        imageUrl,
        null
    );

    // when
    FeedDto result = assembler.toDto(fixture.feed(), null);

    // then
    assertAll(
        () -> assertEquals(1, result.ootds().size()),
        () -> assertEquals(
            imageUrl,
            result.ootds().get(0).imageUrl()
        )
    );

    verifyNoInteractions(imageStorage);
  }

  @Test
  void 이미지가_없는_의상도_OOTD_목록에_포함한다() {
    // given
    FeedFixture fixture = createFeedFixture(
        null,
        null
    );

    // when
    FeedDto result = assembler.toDto(fixture.feed(), null);

    // then
    assertAll(
        () -> assertEquals(1, result.ootds().size()),
        () -> assertEquals(
            fixture.clothes().getId(),
            result.ootds().get(0).clothesId()
        ),
        () -> assertEquals(
            "테스트 의상",
            result.ootds().get(0).name()
        ),
        () -> assertNull(
            result.ootds().get(0).imageUrl()
        )
    );

    verifyNoInteractions(imageStorage);
  }

  private FeedFixture createFeedFixture(
      String imageUrl,
      String imageKey
  ) {
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "이미지 테스트 사용자"
        )
    );

    UUID weatherId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());

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

    Clothes clothes = new Clothes(
        "테스트 의상",
        ClothesType.TOP,
        imageUrl,
        author
    );

    if (imageKey != null) {
      clothes.updateImageKey(imageKey);
    }

    Clothes savedClothes = clothesRepository.saveAndFlush(clothes);

    Feed feed = feedRepository.saveAndFlush(
        new Feed(
            author.getId(),
            weatherId,
            "이미지 응답 테스트 피드"
        )
    );

    feedClothesRepository.saveAndFlush(
        new FeedClothes(feed, savedClothes)
    );

    return new FeedFixture(feed, savedClothes);
  }

  private record FeedFixture(
      Feed feed,
      Clothes clothes
  ) {
  }
}
