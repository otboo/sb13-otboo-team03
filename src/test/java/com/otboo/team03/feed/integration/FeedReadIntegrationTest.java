package com.otboo.team03.feed.integration;

import com.jayway.jsonpath.JsonPath;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@Transactional
class FeedReadIntegrationTest {

  @Container
  static PostgreSQLContainer postgres =
      new PostgreSQLContainer("postgres:17-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        postgres::getJdbcUrl
    );
    registry.add(
        "spring.datasource.username",
        postgres::getUsername
    );
    registry.add(
        "spring.datasource.password",
        postgres::getPassword
    );
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private JwtRegistry jwtRegistry;

  @Test
  void 피드를_생성일시_내림차순으로_첫_페이지_조회한다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "피드 작성자"
        )
    );

    UUID weatherId = UUID.randomUUID();
    insertWeather(weatherId);

    Instant firstTime =
        Instant.parse("2026-10-29T10:00:00Z");
    Instant secondTime =
        Instant.parse("2026-10-29T10:01:00Z");
    Instant thirdTime =
        Instant.parse("2026-10-29T10:02:00Z");

    UUID firstFeedId = insertFeed(
        author.getId(),
        weatherId,
        "첫 번째 피드",
        firstTime
    );

    UUID secondFeedId = insertFeed(
        author.getId(),
        weatherId,
        "두 번째 피드",
        secondTime
    );

    UUID thirdFeedId = insertFeed(
        author.getId(),
        weatherId,
        "세 번째 피드",
        thirdTime
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var result = mockMvc.perform(
        get("/api/feeds")
            .header(
                "Authorization",
                "Bearer " + accessToken
            )
            .param("limit", "2")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .accept(MediaType.APPLICATION_JSON)
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(thirdFeedId.toString()))
        .andExpect(jsonPath("$.data[0].content")
            .value("세 번째 피드"))
        .andExpect(jsonPath("$.data[1].id")
            .value(secondFeedId.toString()))
        .andExpect(jsonPath("$.data[1].content")
            .value("두 번째 피드"))
        .andExpect(jsonPath("$.data[0].author.userId")
            .value(author.getId().toString()))
        .andExpect(jsonPath("$.data[0].weather.weatherId")
            .value(weatherId.toString()))
        .andExpect(jsonPath("$.data[0].ootds").isArray())
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(3))
        .andExpect(jsonPath("$.nextCursor")
            .value(secondTime.toString()))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(secondFeedId.toString()))
        .andExpect(jsonPath("$.sortBy")
            .value("createdAt"))
        .andExpect(jsonPath("$.sortDirection")
            .value("DESCENDING"));
  }


  @Test
  void 커서를_이용해_두번째_페이지를_조회한다() throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "페이지 조회 테스트"
        )
    );

    UUID weatherId = UUID.randomUUID();
    insertWeather(weatherId);

    UUID firstFeedId = insertFeed(
        author.getId(),
        weatherId,
        "첫 번째 피드",
        Instant.parse("2026-10-29T10:00:00Z")
    );

    UUID secondFeedId = insertFeed(
        author.getId(),
        weatherId,
        "두 번째 피드",
        Instant.parse("2026-10-29T10:01:00Z")
    );

    UUID thirdFeedId = insertFeed(
        author.getId(),
        weatherId,
        "세 번째 피드",
        Instant.parse("2026-10-29T10:02:00Z")
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "2")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id")
            .value(thirdFeedId.toString()))
        .andExpect(jsonPath("$.data[1].id")
            .value(secondFeedId.toString()))
        .andReturn();

    String responseBody =
        firstPage.getResponse().getContentAsString();

    String nextCursor =
        JsonPath.read(responseBody, "$.nextCursor");

    String nextIdAfter =
        JsonPath.read(responseBody, "$.nextIdAfter");

    var secondPage = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(firstFeedId.toString()))
        .andExpect(jsonPath("$.data[0].content")
            .value("첫 번째 피드"))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(3));
  }

  @Test
  void 생성일시_오름차순에서_같은시간의_피드도_페이지를_이동한다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "오름차순 테스트"
        )
    );

    UUID weatherId = UUID.randomUUID();
    insertWeather(weatherId);

    Instant firstTime = Instant.parse("2026-10-29T10:00:00Z");
    Instant sameTime = Instant.parse("2026-10-29T10:01:00Z");

    UUID firstFeedId = insertFeed(
        author.getId(),
        weatherId,
        "피드 A",
        firstTime
    );

    UUID secondFeedId = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    UUID thirdFeedId = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );

    insertFeedWithId(
        secondFeedId,
        author.getId(),
        weatherId,
        "피드 B",
        sameTime
    );

    insertFeedWithId(
        thirdFeedId,
        author.getId(),
        weatherId,
        "피드 C",
        sameTime
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "2")
                .param("sortBy", "createdAt")
                .param("sortDirection", "ASCENDING")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(firstFeedId.toString()))
        .andExpect(jsonPath("$.data[1].id")
            .value(secondFeedId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor")
            .value(sameTime.toString()))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(secondFeedId.toString()))
        .andReturn();

    String responseBody =
        firstPage.getResponse().getContentAsString();

    String nextCursor =
        JsonPath.read(responseBody, "$.nextCursor");

    String nextIdAfter =
        JsonPath.read(responseBody, "$.nextIdAfter");

    var secondPage = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("sortBy", "createdAt")
            .param("sortDirection", "ASCENDING")
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(thirdFeedId.toString()))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(3))
        .andExpect(jsonPath("$.nextCursor").isEmpty())
        .andExpect(jsonPath("$.nextIdAfter").isEmpty());
  }

  @Test
  void 생성일시_내림차순에서_같은시간의_피드를_누락없이_조회한다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "내림차순 커서 테스트"
        )
    );

    UUID weatherId = UUID.randomUUID();
    insertWeather(weatherId);

    Instant olderTime = Instant.parse("2026-10-29T10:00:00Z");
    Instant sameTime = Instant.parse("2026-10-29T10:01:00Z");

    UUID olderFeedId = insertFeed(
        author.getId(),
        weatherId,
        "오래된 피드",
        olderTime
    );

    UUID firstId = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    UUID secondId = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );
    UUID thirdId = UUID.fromString(
        "00000000-0000-0000-0000-000000000003"
    );

    insertFeedWithId(
        firstId, author.getId(), weatherId, "동일 시각 A", sameTime
    );
    insertFeedWithId(
        secondId, author.getId(), weatherId, "동일 시각 B", sameTime
    );
    insertFeedWithId(
        thirdId, author.getId(), weatherId, "동일 시각 C", sameTime
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "2")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id")
            .value(thirdId.toString()))
        .andExpect(jsonPath("$.data[1].id")
            .value(secondId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor")
            .value(sameTime.toString()))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(secondId.toString()))
        .andReturn();

    String responseBody =
        firstPage.getResponse().getContentAsString();

    String nextCursor =
        JsonPath.read(responseBody, "$.nextCursor");

    String nextIdAfter =
        JsonPath.read(responseBody, "$.nextIdAfter");

    var secondPage = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    var emptyPage = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("cursor", olderTime.toString())
            .param("idAfter", olderFeedId.toString())
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(firstId.toString()))
        .andExpect(jsonPath("$.data[1].id")
            .value(olderFeedId.toString()))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(4))
        .andExpect(jsonPath("$.nextCursor").isEmpty())
        .andExpect(jsonPath("$.nextIdAfter").isEmpty());

    emptyPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(4));
  }

  @Test
  void 잘못된_커서_요청은_400을_반환한다() throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "커서 검증 테스트"
        )
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    String validCursor = "2026-10-29T10:00:00Z";
    String validIdAfter = UUID.randomUUID().toString();

    // when
    var cursorOnly = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("cursor", validCursor)
    );

    var idAfterOnly = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("idAfter", validIdAfter)
    );

    var invalidCursor = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("cursor", "invalid-cursor")
            .param("idAfter", validIdAfter)
    );

    // then
    cursorOnly.andExpect(status().isBadRequest());
    idAfterOnly.andExpect(status().isBadRequest());
    invalidCursor.andExpect(status().isBadRequest());
  }

  @Test
  void 좋아요수_내림차순으로_첫_페이지를_조회한다() throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "피드 작성자"
        )
    );

    User liker1 = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "좋아요 사용자 1"
        )
    );

    User liker2 = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "좋아요 사용자 2"
        )
    );

    UUID weatherId = UUID.randomUUID();
    insertWeather(weatherId);

    UUID mostLikedFeedId = insertFeed(
        author.getId(),
        weatherId,
        "좋아요 2개 피드",
        Instant.parse("2026-10-29T10:00:00Z")
    );

    UUID mediumLikedFeedId = insertFeed(
        author.getId(),
        weatherId,
        "좋아요 1개 피드",
        Instant.parse("2026-10-29T10:01:00Z")
    );

    UUID leastLikedFeedId = insertFeed(
        author.getId(),
        weatherId,
        "좋아요 0개 피드",
        Instant.parse("2026-10-29T10:02:00Z")
    );

    insertFeedLike(mostLikedFeedId, liker1.getId());
    insertFeedLike(mostLikedFeedId, liker2.getId());
    insertFeedLike(mediumLikedFeedId, liker1.getId());

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var result = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("sortBy", "likeCount")
            .param("sortDirection", "DESCENDING")
            .accept(MediaType.APPLICATION_JSON)
    );

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(mostLikedFeedId.toString()))
        .andExpect(jsonPath("$.data[0].likeCount").value(2))
        .andExpect(jsonPath("$.data[1].id")
            .value(mediumLikedFeedId.toString()))
        .andExpect(jsonPath("$.data[1].likeCount").value(1))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(3))
        .andExpect(jsonPath("$.nextCursor").value("1"))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(mediumLikedFeedId.toString()))
        .andExpect(jsonPath("$.sortBy").value("likeCount"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
  }

  private void insertWeather(UUID weatherId) {
    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update("""
                INSERT INTO weathers (
                    id,
                    latitude,
                    longitude,
                    forecasted_at,
                    forecast_at,
                    sky_status,
                    precipitation_type,
                    precipitation_amount,
                    precipitation_probability,
                    temperature,
                    humidity,
                    wind_speed,
                    created_at,
                    updated_at
                ) VALUES (
                    ?, 35.2, 129.1,
                    ?, ?,
                    'CLEAR', 'NONE',
                    0, 0, 20, 50, 1,
                    ?, ?
                )
                """,
        weatherId,
        now,
        now,
        now,
        now
    );
  }

  private UUID insertFeed(
      UUID authorId,
      UUID weatherId,
      String content,
      Instant createdAt
  ) {
    return insertFeedWithId(
        UUID.randomUUID(),
        authorId,
        weatherId,
        content,
        createdAt
    );
  }

  private UUID insertFeedWithId(
      UUID feedId,
      UUID authorId,
      UUID weatherId,
      String content,
      Instant createdAt
  ) {
    Timestamp timestamp = Timestamp.from(createdAt);

    jdbcTemplate.update("""
      INSERT INTO feeds (
          id,
          author_id,
          weather_id,
          content,
          created_at,
          updated_at
      ) VALUES (?, ?, ?, ?, ?, ?)
      """,
        feedId,
        authorId,
        weatherId,
        content,
        timestamp,
        timestamp
    );

    return feedId;
  }

  private void insertFeedLike(UUID feedId, UUID userId) {
    jdbcTemplate.update("""
      INSERT INTO feed_likes (
          id,
          feed_id,
          user_id,
          created_at
      ) VALUES (?, ?, ?, ?)
      """,
        UUID.randomUUID(),
        feedId,
        userId,
        Timestamp.from(Instant.now())
    );
  }
}
