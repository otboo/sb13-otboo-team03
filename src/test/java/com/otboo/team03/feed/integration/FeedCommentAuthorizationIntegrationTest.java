package com.otboo.team03.feed.integration;

import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.global.security.jwt.JwtRegistry;
import com.otboo.team03.global.security.jwt.JwtTokenProvider;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedCommentAuthorizationIntegrationTest
    extends PostgresTestSupport {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private JwtTokenProvider jwtTokenProvider;

  @MockitoBean
  private JwtRegistry jwtRegistry;

  @Test
  void 미인증_사용자는_댓글을_등록하거나_조회할_수_없다()
      throws Exception {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    String requestJson = createRequestJson(
        feedId, authorId, "좋은 코디네요!"
    );

    // when
    var postResult = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", feedId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    var getResult = mockMvc.perform(
        get("/api/feeds/{feedId}/comments", feedId)
            .param("limit", "10")
    );

    // then
    postResult.andExpect(status().isUnauthorized());
    getResult.andExpect(status().isUnauthorized());

    assertCommentCount(feedId, 0L);
  }

  @Test
  void 인증_사용자와_댓글_작성자가_다르면_403이고_저장되지_않는다()
      throws Exception {
    // given
    TestFixture fixture = createFixture();
    UUID otherAuthorId = UUID.randomUUID();

    String requestJson = createRequestJson(
        fixture.feedId(),
        otherAuthorId,
        "타인 명의 댓글"
    );

    // when
    var result = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", fixture.feedId())
            .with(csrf())
            .header(
                "Authorization",
                "Bearer " + fixture.accessToken()
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName")
            .value("ACCESS_DENIED"));

    assertCommentCount(fixture.feedId(), 0L);
  }

  @Test
  void 존재하지_않는_피드의_댓글_등록과_조회는_404를_반환한다()
      throws Exception {
    // given
    TestFixture fixture = createFixture();
    UUID nonexistentFeedId = UUID.randomUUID();

    String requestJson = createRequestJson(
        nonexistentFeedId,
        fixture.userId(),
        "존재하지 않는 피드"
    );

    // when
    var postResult = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", nonexistentFeedId)
            .with(csrf())
            .header(
                "Authorization",
                "Bearer " + fixture.accessToken()
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    var getResult = mockMvc.perform(
        get("/api/feeds/{feedId}/comments", nonexistentFeedId)
            .header(
                "Authorization",
                "Bearer " + fixture.accessToken()
            )
            .param("limit", "10")
    );

    // then
    postResult.andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("NOT_FOUND_RESOURCE"));

    getResult.andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName")
            .value("NOT_FOUND_RESOURCE"));

    assertCommentCount(nonexistentFeedId, 0L);
    assertCommentCount(fixture.feedId(), 0L);
  }

  @Test
  void 공백_댓글은_400이고_DB에_저장되지_않는다()
      throws Exception {
    // given
    TestFixture fixture = createFixture();

    String requestJson = createRequestJson(
        fixture.feedId(),
        fixture.userId(),
        "   "
    );

    // when
    var result = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", fixture.feedId())
            .with(csrf())
            .header(
                "Authorization",
                "Bearer " + fixture.accessToken()
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    assertCommentCount(fixture.feedId(), 0L);
  }

  @Test
  void 경로와_요청_본문의_피드_ID가_다르면_400이다()
      throws Exception {
    // given
    TestFixture fixture = createFixture();
    UUID otherFeedId = UUID.randomUUID();

    String requestJson = createRequestJson(
        otherFeedId,
        fixture.userId(),
        "잘못된 피드 ID"
    );

    // when
    var result = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", fixture.feedId())
            .with(csrf())
            .header(
                "Authorization",
                "Bearer " + fixture.accessToken()
            )
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName")
            .value("INVALID_INPUT_VALUE"));

    assertCommentCount(fixture.feedId(), 0L);
    assertCommentCount(otherFeedId, 0L);
  }

  private TestFixture createFixture() {
    User user = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "댓글 작성자"
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
        ) VALUES (
            ?, 35.2, 129.1,
            ?, ?, 'CLEAR', 'NONE',
            0, 0, 20, 50, 1,
            ?, ?
        )
        """,
        weatherId, now, now, now, now
    );

    Feed feed = feedRepository.saveAndFlush(
        new Feed(
            user.getId(),
            weatherId,
            "댓글 인증 테스트 피드"
        )
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(user.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    return new TestFixture(
        feed.getId(),
        user.getId(),
        accessToken
    );
  }

  private String createRequestJson(
      UUID feedId,
      UUID authorId,
      String content
  ) {
    return """
        {
          "feedId": "%s",
          "authorId": "%s",
          "content": "%s"
        }
        """.formatted(feedId, authorId, content);
  }

  private void assertCommentCount(
      UUID feedId,
      long expectedCount
  ) {
    entityManager.flush();

    Long actualCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feed_comments WHERE feed_id = ?",
        Long.class,
        feedId
    );

    assertEquals(expectedCount, actualCount);
  }

  private record TestFixture(
      UUID feedId,
      UUID userId,
      String accessToken
  ) {
  }
}
