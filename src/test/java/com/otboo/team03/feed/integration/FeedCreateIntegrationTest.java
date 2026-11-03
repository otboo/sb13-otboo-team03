package com.otboo.team03.feed.integration;

import com.jayway.jsonpath.JsonPath;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.mapper.FeedDtoAssembler;
import com.otboo.team03.feed.repository.FeedClothesRepository;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FeedCreateIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

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

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private JwtRegistry jwtRegistry;

  @MockitoSpyBean
  private FeedDtoAssembler feedDtoAssembler;

  private UUID authorId;
  private UUID weatherId;
  private UUID clothesId;
  private UUID definitionId;

  @BeforeEach
  void setUp() {
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "통합 테스트 작성자"
        )
    );
    authorId = author.getId();

    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update("""
                INSERT INTO profiles (
                    user_id,
                    profile_image_url,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?)
                """,
        authorId,
        "/images/profile.png",
        now,
        now
    );

    weatherId = UUID.randomUUID();

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
    clothesId = clothes.getId();

    definitionId = UUID.randomUUID();

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
        clothesId,
        definitionId,
        "흰색"
    );
  }

  @AfterEach
  void tearDown() {
    if (authorId != null) {
      jdbcTemplate.update("""
                    DELETE FROM feed_likes
                    WHERE feed_id IN (
                        SELECT id FROM feeds WHERE author_id = ?
                    )
                    """, authorId);

      jdbcTemplate.update("""
                    DELETE FROM feed_comments
                    WHERE feed_id IN (
                        SELECT id FROM feeds WHERE author_id = ?
                    )
                    """, authorId);

      jdbcTemplate.update("""
                    DELETE FROM feed_clothes
                    WHERE feed_id IN (
                        SELECT id FROM feeds WHERE author_id = ?
                    )
                    """, authorId);

      jdbcTemplate.update(
          "DELETE FROM feeds WHERE author_id = ?",
          authorId
      );
    }

    if (clothesId != null) {
      jdbcTemplate.update(
          "DELETE FROM clothes_attribute WHERE clothes_id = ?",
          clothesId
      );

      jdbcTemplate.update(
          "DELETE FROM clothes WHERE id = ?",
          clothesId
      );
    }

    if (definitionId != null) {
      jdbcTemplate.update(
          "DELETE FROM clothes_attribute_definitions WHERE id = ?",
          definitionId
      );
    }

    if (authorId != null) {
      jdbcTemplate.update(
          "DELETE FROM profiles WHERE user_id = ?",
          authorId
      );

      jdbcTemplate.update(
          "DELETE FROM users WHERE id = ?",
          authorId
      );
    }

    if (weatherId != null) {
      jdbcTemplate.update(
          "DELETE FROM weathers WHERE id = ?",
          weatherId
      );
    }
  }

  @Test
  void 인증된_사용자가_피드를_생성하면_201과_저장된_응답을_반환한다()
      throws Exception {
    // given
    String accessToken = authenticatedAccessToken();

    // when
    MvcResult result = mockMvc.perform(
            post("/api/feeds")
                .with(csrf())
                .header(
                    "Authorization",
                    "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson())
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.updatedAt").isNotEmpty())
        .andExpect(jsonPath("$.content")
            .value("오늘의 코디"))
        .andExpect(jsonPath("$.author.userId")
            .value(authorId.toString()))
        .andExpect(jsonPath("$.author.name")
            .value("통합 테스트 작성자"))
        .andExpect(jsonPath("$.author.profileImageUrl")
            .value("/images/profile.png"))
        .andExpect(jsonPath("$.weather.weatherId")
            .value(weatherId.toString()))
        .andExpect(jsonPath("$.weather.skyStatus")
            .value("CLEAR"))
        .andExpect(jsonPath("$.weather.temperature.current")
            .value(20.0))
        .andExpect(jsonPath("$.ootds[0].clothesId")
            .value(clothesId.toString()))
        .andExpect(jsonPath("$.ootds[0].name")
            .value("흰색 티셔츠"))
        .andExpect(jsonPath("$.ootds[0].type")
            .value("TOP"))
        .andExpect(jsonPath("$.ootds[0].attributes[0].definitionName")
            .value("색상"))
        .andExpect(jsonPath("$.ootds[0].attributes[0].value")
            .value("흰색"))
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.commentCount").value(0))
        .andExpect(jsonPath("$.likedByMe").value(false))
        .andReturn();

    // then
    UUID savedFeedId = jdbcTemplate.queryForObject(
        "SELECT id FROM feeds WHERE author_id = ?",
        UUID.class,
        authorId
    );

    String responseFeedId = JsonPath.read(
        result.getResponse().getContentAsString(),
        "$.id"
    );

    Long savedFeedCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feeds WHERE id = ?",
        Long.class,
        savedFeedId
    );

    Long savedClothesCount = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM feed_clothes
        WHERE feed_id = ? AND clothes_id = ?
        """,
        Long.class,
        savedFeedId,
        clothesId
    );

    assertThat(responseFeedId).isEqualTo(savedFeedId.toString());
    assertThat(savedFeedCount).isEqualTo(1L);
    assertThat(savedClothesCount).isEqualTo(1L);
  }

  @Test
  void 인증하지_않으면_피드를_생성할_수_없다() throws Exception {
    // given
    String requestJson = createRequestJson();

    // when
    MvcResult result = mockMvc.perform(
            post("/api/feeds")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson)
        )
        .andExpect(status().isUnauthorized())
        .andReturn();

    // then
    Long feedCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feeds WHERE author_id = ?",
        Long.class,
        authorId
    );

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(feedCount).isZero();
  }

  @Test
  void 응답_변환중_오류가_발생하면_피드와_연결정보가_롤백된다()
      throws Exception {
    // given
    String accessToken = authenticatedAccessToken();

    doAnswer(invocation -> {
      feedRepository.flush();
      feedClothesRepository.flush();

      throw new IllegalStateException(
          "테스트용 응답 변환 실패"
      );
    }).when(feedDtoAssembler)
        .toDto(any(Feed.class), isNull());

    // when
    MvcResult result = mockMvc.perform(
            post("/api/feeds")
                .with(csrf())
                .header(
                    "Authorization",
                    "Bearer " + accessToken
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(createRequestJson())
        )
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.exceptionName")
            .value("INTERNAL_SERVER_ERROR"))
        .andReturn();

    // then
    Long feedCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feeds WHERE author_id = ?",
        Long.class,
        authorId
    );

    Long feedClothesCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feed_clothes WHERE clothes_id = ?",
        Long.class,
        clothesId
    );

    assertThat(result.getResponse().getStatus()).isEqualTo(500);
    assertThat(feedCount).isZero();
    assertThat(feedClothesCount).isZero();
  }

  private String authenticatedAccessToken() {
    String token = jwtTokenProvider.createAccessToken(authorId);

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(token))
        .thenReturn(true);

    return token;
  }

  private String createRequestJson() {
    return """
                {
                  "authorId": "%s",
                  "weatherId": "%s",
                  "clothesIds": ["%s"],
                  "content": "오늘의 코디"
                }
                """.formatted(
        authorId,
        weatherId,
        clothesId
    );
  }
}
