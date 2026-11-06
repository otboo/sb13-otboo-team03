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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedCommentIntegrationTest extends PostgresTestSupport {

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
  void 댓글_등록과_조회가_DB와_피드_댓글수에_반영된다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "피드 작성자"
        )
    );

    User commenter = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "댓글 작성자"
        )
    );

    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update("""
        INSERT INTO profiles (
            user_id, profile_image_url,
            created_at, updated_at
        ) VALUES (?, ?, ?, ?)
        """,
        commenter.getId(),
        "/images/commenter.png",
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
            author.getId(),
            weatherId,
            "오늘의 코디"
        )
    );

    UUID feedId = feed.getId();

    String accessToken =
        jwtTokenProvider.createAccessToken(commenter.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    String requestJson = """
        {
          "feedId": "%s",
          "authorId": "%s",
          "content": "좋은 코디네요!"
        }
        """.formatted(feedId, commenter.getId());

    // when
    var postResult = mockMvc.perform(
        post("/api/feeds/{feedId}/comments", feedId)
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
    );

    // then
    postResult.andExpect(status().isOk())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.feedId")
            .value(feedId.toString()))
        .andExpect(jsonPath("$.author.userId")
            .value(commenter.getId().toString()))
        .andExpect(jsonPath("$.author.name")
            .value("댓글 작성자"))
        .andExpect(jsonPath("$.author.profileImageUrl")
            .value("/images/commenter.png"))
        .andExpect(jsonPath("$.content")
            .value("좋은 코디네요!"));

    entityManager.flush();
    entityManager.clear();

    Long savedCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feed_comments WHERE feed_id = ?",
        Long.class,
        feedId
    );

    assertEquals(1L, savedCount);

    UUID savedCommentId = jdbcTemplate.queryForObject(
        "SELECT id FROM feed_comments WHERE feed_id = ?",
        UUID.class,
        feedId
    );

    assertNotNull(savedCommentId);

    postResult.andExpect(jsonPath("$.id")
        .value(savedCommentId.toString()));

    UUID savedAuthorId = jdbcTemplate.queryForObject(
        "SELECT author_id FROM feed_comments WHERE id = ?",
        UUID.class,
        savedCommentId
    );

    String savedContent = jdbcTemplate.queryForObject(
        "SELECT content FROM feed_comments WHERE id = ?",
        String.class,
        savedCommentId
    );

    assertEquals(commenter.getId(), savedAuthorId);
    assertEquals("좋은 코디네요!", savedContent);

    // when
    var commentsResult = mockMvc.perform(
        get("/api/feeds/{feedId}/comments", feedId)
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "10")
    );

    var feedsResult = mockMvc.perform(
        get("/api/feeds")
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
    );

    // then
    commentsResult.andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id")
            .value(savedCommentId.toString()))
        .andExpect(jsonPath("$.data[0].feedId")
            .value(feedId.toString()))
        .andExpect(jsonPath("$.data[0].author.userId")
            .value(commenter.getId().toString()))
        .andExpect(jsonPath("$.data[0].author.name")
            .value("댓글 작성자"))
        .andExpect(jsonPath("$.data[0].author.profileImageUrl")
            .value("/images/commenter.png"))
        .andExpect(jsonPath("$.data[0].content")
            .value("좋은 코디네요!"))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.sortBy").value("createdAt"))
        .andExpect(jsonPath("$.sortDirection")
            .value("DESCENDING"));

    feedsResult.andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id")
            .value(feedId.toString()))
        .andExpect(jsonPath("$.data[0].commentCount")
            .value(1));
  }
}
