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
    UUID feedId = UUID.randomUUID();
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
}
