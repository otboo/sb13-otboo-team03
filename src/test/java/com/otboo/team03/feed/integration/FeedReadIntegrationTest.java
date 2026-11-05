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

  @Test
  void 좋아요수_내림차순에서_커서로_두번째_페이지를_조회한다()
      throws Exception {
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

    Instant createdAt =
        Instant.parse("2026-10-29T10:00:00Z");

    UUID mostLikedFeedId = insertFeed(
        author.getId(),
        weatherId,
        "좋아요 2개",
        createdAt
    );

    UUID higherId = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );

    UUID lowerId = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );

    insertFeedWithId(
        higherId,
        author.getId(),
        weatherId,
        "좋아요 1개 B",
        createdAt
    );

    insertFeedWithId(
        lowerId,
        author.getId(),
        weatherId,
        "좋아요 1개 C",
        createdAt
    );

    UUID zeroLikedFeedId = insertFeed(
        author.getId(),
        weatherId,
        "좋아요 0개",
        createdAt
    );

    insertFeedLike(mostLikedFeedId, liker1.getId());
    insertFeedLike(mostLikedFeedId, liker2.getId());
    insertFeedLike(higherId, liker1.getId());
    insertFeedLike(lowerId, liker2.getId());

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "2")
                .param("sortBy", "likeCount")
                .param("sortDirection", "DESCENDING")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(mostLikedFeedId.toString()))
        .andExpect(jsonPath("$.data[1].id")
            .value(higherId.toString()))
        .andExpect(jsonPath("$.nextCursor").value("1"))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(higherId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
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
            .param("sortBy", "likeCount")
            .param("sortDirection", "DESCENDING")
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(lowerId.toString()))
        .andExpect(jsonPath("$.data[0].likeCount").value(1))
        .andExpect(jsonPath("$.data[1].id")
            .value(zeroLikedFeedId.toString()))
        .andExpect(jsonPath("$.data[1].likeCount").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(4))
        .andExpect(jsonPath("$.nextCursor").isEmpty())
        .andExpect(jsonPath("$.nextIdAfter").isEmpty());
  }

  @Test
  void 좋아요수_오름차순에서_같은좋아요수의_피드도_페이지를_이동한다()
      throws Exception {
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

    Instant createdAt =
        Instant.parse("2026-10-29T10:00:00Z");

    UUID zeroLikedFeedId = insertFeed(
        author.getId(),
        weatherId,
        "좋아요 0개",
        createdAt
    );

    UUID lowerId = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );

    UUID higherId = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );

    insertFeedWithId(
        lowerId,
        author.getId(),
        weatherId,
        "좋아요 1개 A",
        createdAt
    );

    insertFeedWithId(
        higherId,
        author.getId(),
        weatherId,
        "좋아요 1개 B",
        createdAt
    );

    UUID mostLikedFeedId = insertFeed(
        author.getId(),
        weatherId,
        "좋아요 2개",
        createdAt
    );

    insertFeedLike(lowerId, liker1.getId());
    insertFeedLike(higherId, liker2.getId());
    insertFeedLike(mostLikedFeedId, liker1.getId());
    insertFeedLike(mostLikedFeedId, liker2.getId());

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "2")
                .param("sortBy", "likeCount")
                .param("sortDirection", "ASCENDING")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(zeroLikedFeedId.toString()))
        .andExpect(jsonPath("$.data[0].likeCount").value(0))
        .andExpect(jsonPath("$.data[1].id")
            .value(lowerId.toString()))
        .andExpect(jsonPath("$.data[1].likeCount").value(1))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor").value("1"))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(lowerId.toString()))
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
            .param("sortBy", "likeCount")
            .param("sortDirection", "ASCENDING")
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(higherId.toString()))
        .andExpect(jsonPath("$.data[0].likeCount").value(1))
        .andExpect(jsonPath("$.data[1].id")
            .value(mostLikedFeedId.toString()))
        .andExpect(jsonPath("$.data[1].likeCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(4))
        .andExpect(jsonPath("$.sortBy").value("likeCount"))
        .andExpect(jsonPath("$.sortDirection").value("ASCENDING"))
        .andExpect(jsonPath("$.nextCursor").isEmpty())
        .andExpect(jsonPath("$.nextIdAfter").isEmpty());
  }

  @Test
  void 키워드로_검색한_피드를_커서로_조회한다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "키워드 검색 테스트"
        )
    );

    UUID weatherId = UUID.randomUUID();
    insertWeather(weatherId);

    UUID firstMatchId = insertFeed(
        author.getId(),
        weatherId,
        "주말 코디 추천",
        Instant.parse("2026-10-29T10:00:00Z")
    );

    insertFeed(
        author.getId(),
        weatherId,
        "오늘 날씨 맑음",
        Instant.parse("2026-10-29T10:01:00Z")
    );

    UUID secondMatchId = insertFeed(
        author.getId(),
        weatherId,
        "출근 코디 공유",
        Instant.parse("2026-10-29T10:02:00Z")
    );

    insertFeed(
        author.getId(),
        weatherId,
        "운동 다녀옴",
        Instant.parse("2026-10-29T10:03:00Z")
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "1")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING")
                .param("keywordLike", "코디")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(secondMatchId.toString()))
        .andExpect(jsonPath("$.data[0].content")
            .value("출근 코디 공유"))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(true))
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
            .param("limit", "1")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("keywordLike", "코디")
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(firstMatchId.toString()))
        .andExpect(jsonPath("$.data[0].content")
            .value("주말 코디 추천"))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").isEmpty())
        .andExpect(jsonPath("$.nextIdAfter").isEmpty());
  }

  @Test
  void 작성자별_피드를_커서로_조회한다() throws Exception {
    // given
    User authorA = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "작성자 A"
        )
    );

    User authorB = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "작성자 B"
        )
    );

    UUID weatherId = UUID.randomUUID();
    insertWeather(weatherId);

    UUID firstFeedId = insertFeed(
        authorA.getId(),
        weatherId,
        "작성자 A의 첫 번째 피드",
        Instant.parse("2026-10-29T10:00:00Z")
    );

    insertFeed(
        authorB.getId(),
        weatherId,
        "작성자 B의 첫 번째 피드",
        Instant.parse("2026-10-29T10:01:00Z")
    );

    UUID secondFeedId = insertFeed(
        authorA.getId(),
        weatherId,
        "작성자 A의 두 번째 피드",
        Instant.parse("2026-10-29T10:02:00Z")
    );

    insertFeed(
        authorB.getId(),
        weatherId,
        "작성자 B의 두 번째 피드",
        Instant.parse("2026-10-29T10:03:00Z")
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(authorA.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "1")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING")
                .param("authorIdEqual", authorA.getId().toString())
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(secondFeedId.toString()))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(true))
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
            .param("limit", "1")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("authorIdEqual", authorA.getId().toString())
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(firstFeedId.toString()))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void 하늘상태로_필터링한_피드를_커서로_조회한다() throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "날씨 필터 테스트"
        )
    );

    UUID clearWeatherId = insertWeather("CLEAR", "NONE");
    UUID cloudyWeatherId = insertWeather("CLOUDY", "NONE");

    UUID olderClearFeedId = insertFeed(
        author.getId(),
        clearWeatherId,
        "맑은 날 첫 번째 코디",
        Instant.parse("2026-10-29T10:00:00Z")
    );

    insertFeed(
        author.getId(),
        cloudyWeatherId,
        "흐린 날 코디",
        Instant.parse("2026-10-29T10:01:00Z")
    );

    UUID newerClearFeedId = insertFeed(
        author.getId(),
        clearWeatherId,
        "맑은 날 두 번째 코디",
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
                .param("limit", "1")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING")
                .param("skyStatusEqual", "CLEAR")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(newerClearFeedId.toString()))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(true))
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
            .param("limit", "1")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("skyStatusEqual", "CLEAR")
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(olderClearFeedId.toString()))
        .andExpect(jsonPath("$.data[0].weather.skyStatus")
            .value("CLEAR"))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void 강수형태로_피드를_필터링한다() throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "강수 필터 테스트"
        )
    );

    UUID rainWeatherId = insertWeather("CLOUDY", "RAIN");
    UUID noneWeatherId = insertWeather("CLEAR", "NONE");

    UUID olderRainFeedId = insertFeed(
        author.getId(),
        rainWeatherId,
        "비 오는 날 첫 번째 코디",
        Instant.parse("2026-10-29T10:00:00Z")
    );

    insertFeed(
        author.getId(),
        noneWeatherId,
        "비가 오지 않는 날 코디",
        Instant.parse("2026-10-29T10:01:00Z")
    );

    UUID newerRainFeedId = insertFeed(
        author.getId(),
        rainWeatherId,
        "비 오는 날 두 번째 코디",
        Instant.parse("2026-10-29T10:02:00Z")
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var result = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("precipitationTypeEqual", "RAIN")
    );

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(newerRainFeedId.toString()))
        .andExpect(jsonPath("$.data[1].id")
            .value(olderRainFeedId.toString()))
        .andExpect(jsonPath("$.data[0].weather.precipitation.type")
            .value("RAIN"))
        .andExpect(jsonPath("$.data[1].weather.precipitation.type")
            .value("RAIN"))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void 키워드_작성자_날씨_강수형태를_동시에_필터링한다()
      throws Exception {
    // given
    User authorA = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "작성자 A"
        )
    );

    User authorB = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "작성자 B"
        )
    );

    UUID clearRainId = insertWeather("CLEAR", "RAIN");
    UUID clearNoneId = insertWeather("CLEAR", "NONE");
    UUID cloudyRainId = insertWeather("CLOUDY", "RAIN");

    UUID expectedFeedId = insertFeed(
        authorA.getId(),
        clearRainId,
        "오늘의 코디",
        Instant.parse("2026-10-29T10:00:00Z")
    );

    insertFeed(
        authorA.getId(),
        clearNoneId,
        "강수가 다른 코디",
        Instant.parse("2026-10-29T10:01:00Z")
    );

    insertFeed(
        authorA.getId(),
        cloudyRainId,
        "하늘 상태가 다른 코디",
        Instant.parse("2026-10-29T10:02:00Z")
    );

    insertFeed(
        authorB.getId(),
        clearRainId,
        "작성자가 다른 코디",
        Instant.parse("2026-10-29T10:03:00Z")
    );

    insertFeed(
        authorA.getId(),
        clearRainId,
        "검색어가 다른 옷차림",
        Instant.parse("2026-10-29T10:04:00Z")
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(authorA.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var result = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("keywordLike", "코디")
            .param("authorIdEqual", authorA.getId().toString())
            .param("skyStatusEqual", "CLEAR")
            .param("precipitationTypeEqual", "RAIN")
    );

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(expectedFeedId.toString()))
        .andExpect(jsonPath("$.data[0].author.userId")
            .value(authorA.getId().toString()))
        .andExpect(jsonPath("$.data[0].weather.skyStatus")
            .value("CLEAR"))
        .andExpect(jsonPath("$.data[0].weather.precipitation.type")
            .value("RAIN"))
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void 날씨_필터에서_좋아요수_내림차순_커서_조회한다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "날씨 좋아요 테스트"
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

    UUID targetWeather = insertWeather("CLEAR", "RAIN");
    UUID otherWeather = insertWeather("CLOUDY", "RAIN");

    UUID mostLikedId = insertFeed(
        author.getId(),
        targetWeather,
        "좋아요 2개",
        Instant.parse("2026-10-29T10:00:00Z")
    );

    UUID mediumLikedId = insertFeed(
        author.getId(),
        targetWeather,
        "좋아요 1개",
        Instant.parse("2026-10-29T10:01:00Z")
    );

    UUID zeroLikedId = insertFeed(
        author.getId(),
        targetWeather,
        "좋아요 0개",
        Instant.parse("2026-10-29T10:02:00Z")
    );

    UUID excludedId = insertFeed(
        author.getId(),
        otherWeather,
        "날씨가 다른 피드",
        Instant.parse("2026-10-29T10:03:00Z")
    );

    insertFeedLike(mostLikedId, liker1.getId());
    insertFeedLike(mostLikedId, liker2.getId());
    insertFeedLike(mediumLikedId, liker1.getId());

    insertFeedLike(excludedId, liker1.getId());
    insertFeedLike(excludedId, liker2.getId());
    insertFeedLike(excludedId, author.getId());

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "2")
                .param("sortBy", "likeCount")
                .param("sortDirection", "DESCENDING")
                .param("skyStatusEqual", "CLEAR")
                .param("precipitationTypeEqual", "RAIN")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(mostLikedId.toString()))
        .andExpect(jsonPath("$.data[0].likeCount").value(2))
        .andExpect(jsonPath("$.data[1].id")
            .value(mediumLikedId.toString()))
        .andExpect(jsonPath("$.data[1].likeCount").value(1))
        .andExpect(jsonPath("$.totalCount").value(3))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor").value("1"))
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
            .param("sortBy", "likeCount")
            .param("sortDirection", "DESCENDING")
            .param("skyStatusEqual", "CLEAR")
            .param("precipitationTypeEqual", "RAIN")
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(zeroLikedId.toString()))
        .andExpect(jsonPath("$.data[0].likeCount").value(0))
        .andExpect(jsonPath("$.totalCount").value(3))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void 날씨_필터에서_좋아요수_오름차순_동점_커서를_조회한다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "좋아요 오름차순 테스트"
        )
    );

    UUID targetWeather = insertWeather("CLEAR", "NONE");
    UUID otherWeather = insertWeather("CLOUDY", "NONE");

    UUID firstId = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );

    UUID secondId = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );

    Instant createdAt = Instant.parse("2026-10-29T10:00:00Z");

    insertFeedWithId(
        firstId,
        author.getId(),
        targetWeather,
        "좋아요 0개 A",
        createdAt
    );

    insertFeedWithId(
        secondId,
        author.getId(),
        targetWeather,
        "좋아요 0개 B",
        createdAt
    );

    insertFeed(
        author.getId(),
        otherWeather,
        "제외할 피드",
        createdAt
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "1")
                .param("sortBy", "likeCount")
                .param("sortDirection", "ASCENDING")
                .param("skyStatusEqual", "CLEAR")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id")
            .value(firstId.toString()))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.nextCursor").value("0"))
        .andExpect(jsonPath("$.hasNext").value(true))
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
            .param("limit", "1")
            .param("sortBy", "likeCount")
            .param("sortDirection", "ASCENDING")
            .param("skyStatusEqual", "CLEAR")
            .param("cursor", nextCursor)
            .param("idAfter", nextIdAfter)
    );

    // then
    secondPage
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(secondId.toString()))
        .andExpect(jsonPath("$.totalCount").value(2))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void 잘못된_날씨_필터는_400을_반환한다() throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "잘못된 날씨 테스트"
        )
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var invalidSkyStatus = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("skyStatusEqual", "SUNNY")
    );

    var invalidPrecipitation = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("precipitationTypeEqual", "THUNDER")
    );

    // then
    invalidSkyStatus.andExpect(status().isBadRequest());
    invalidPrecipitation.andExpect(status().isBadRequest());
  }

  @Test
  void 날씨_필터_검색결과가_없으면_빈_페이지를_반환한다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "빈 검색 결과 테스트"
        )
    );

    UUID weatherId = insertWeather("CLEAR", "NONE");

    insertFeed(
        author.getId(),
        weatherId,
        "맑은 날 코디",
        Instant.parse("2026-10-29T10:00:00Z")
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var result = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("skyStatusEqual", "CLEAR")
            .param("precipitationTypeEqual", "RAIN")
    );

    // then
    result
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.totalCount").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").isEmpty())
        .andExpect(jsonPath("$.nextIdAfter").isEmpty());
  }

  @Test
  void 잘못된_좋아요수_커서는_400을_반환한다() throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "커서 검증 사용자"
        )
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    String idAfter = UUID.randomUUID().toString();

    // when & then
    for (String cursor : java.util.List.of("invalid", "-1")) {
      mockMvc.perform(
          get("/api/feeds")
              .header("Authorization", "Bearer " + accessToken)
              .param("limit", "10")
              .param("sortBy", "likeCount")
              .param("sortDirection", "DESCENDING")
              .param("cursor", cursor)
              .param("idAfter", idAfter)
      ).andExpect(status().isBadRequest());
    }
  }

  @Test
  void 지원하지_않는_정렬조건은_400을_반환한다() throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "정렬 검증 사용자"
        )
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var result = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "10")
            .param("sortBy", "invalid")
            .param("sortDirection", "DESCENDING")
    );

    // then
    result.andExpect(status().isBadRequest());
  }

  /**
   * 상세 조회의 HTTP 응답과 인증 경계를 검증한다.
   *
   * <p>실제 테스트 DB에 사용자·날씨·피드를 저장하고 JWT를 전달해 본문과 빈 의상 목록을
   * 확인한다. 같은 인증 조건에서 존재하지 않는 ID는 404, 인증이 없는 요청은 401이어야 한다.
   * 이미지 key·기존 URL·재서명 규칙 자체는 FeedDtoAssemblerTest에서 별도로 검증한다.
   */
  @Test
  void 상세조회는_본문을_반환하고_없는_피드는_404다() throws Exception {
    User author = userRepository.saveAndFlush(
        new User(UUID.randomUUID() + "@example.com", "encodedPassword", "상세 조회"));
    UUID weatherId = UUID.randomUUID();
    insertWeather(weatherId);
    UUID feedId = insertFeed(author.getId(), weatherId, "보존할 본문", Instant.now());
    String token = jwtTokenProvider.createAccessToken(author.getId());
    when(jwtRegistry.hasActiveJwtInformationByAccessToken(token)).thenReturn(true);

    mockMvc.perform(get("/api/feeds/{feedId}", feedId)
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(feedId.toString()))
        .andExpect(jsonPath("$.content").value("보존할 본문"))
        .andExpect(jsonPath("$.ootds").isEmpty());
    mockMvc.perform(get("/api/feeds/{feedId}", UUID.randomUUID())
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/feeds/{feedId}", feedId))
        .andExpect(status().isUnauthorized());
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

  private UUID insertWeather(
      String skyStatus,
      String precipitationType
  ) {
    UUID weatherId = UUID.randomUUID();

    insertWeather(weatherId);

    jdbcTemplate.update(
        """
        UPDATE weathers
        SET sky_status = ?,
            precipitation_type = ?
        WHERE id = ?
        """,
        skyStatus,
        precipitationType,
        weatherId
    );

    return weatherId;
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
