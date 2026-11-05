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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedLikeIntegrationTest extends PostgresTestSupport {

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
  void 좋아요_등록_중복_취소가_DB와_피드_조회에_반영된다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "피드 작성자"
        )
    );

    User liker = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "좋아요 사용자"
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
            ?, ?,
            'CLEAR', 'NONE',
            0, 0, 20, 50, 1,
            ?, ?
        )
        """,
        weatherId, now, now, now, now
    );

    Feed feed = feedRepository.saveAndFlush(
        new Feed(author.getId(), weatherId, "좋아요 테스트 피드")
    );

    UUID feedId = feed.getId();

    String accessToken =
        jwtTokenProvider.createAccessToken(liker.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    mockMvc.perform(
        post("/api/feeds/{feedId}/like", feedId)
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
    ).andExpect(status().isNoContent());

    entityManager.flush();
    entityManager.clear();

    // then
    assertLikeState(feedId, liker.getId(), 1L, true, accessToken);

    // when
    mockMvc.perform(
        post("/api/feeds/{feedId}/like", feedId)
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
    ).andExpect(status().isNoContent());

    entityManager.flush();
    entityManager.clear();

    // then
    assertLikeState(feedId, liker.getId(), 1L, true, accessToken);

    // when
    mockMvc.perform(
        delete("/api/feeds/{feedId}/like", feedId)
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
    ).andExpect(status().isNoContent());

    entityManager.flush();
    entityManager.clear();

    // then
    assertLikeState(feedId, liker.getId(), 0L, false, accessToken);

    // when
    mockMvc.perform(
        delete("/api/feeds/{feedId}/like", feedId)
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
    ).andExpect(status().isNoContent());

    entityManager.flush();
    entityManager.clear();

    // then
    assertLikeState(feedId, liker.getId(), 0L, false, accessToken);
  }

  private void assertLikeState(
      UUID feedId,
      UUID userId,
      long expectedCount,
      boolean expectedLikedByMe,
      String accessToken
  ) throws Exception {
    Long savedCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM feed_likes
        WHERE feed_id = ? AND user_id = ?
        """,
        Long.class,
        feedId,
        userId
    );

    assertEquals(expectedCount, savedCount);

    mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + accessToken)
                .param("limit", "10")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id")
            .value(feedId.toString()))
        .andExpect(jsonPath("$.data[0].likeCount")
            .value((int) expectedCount))
        .andExpect(jsonPath("$.data[0].likedByMe")
            .value(expectedLikedByMe));
  }

  @Test
  void 한_사용자가_좋아요를_취소해도_다른_사용자의_좋아요는_유지된다()
      throws Exception {
    // given
    User userA = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "사용자 A"
        )
    );

    User userB = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "사용자 B"
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
          0, 0, 20, 50, 1, ?, ?
      )
      """,
        weatherId, now, now, now, now
    );

    Feed feed = feedRepository.saveAndFlush(
        new Feed(userA.getId(), weatherId, "좋아요 테스트 피드")
    );

    UUID feedId = feed.getId();

    String tokenA = jwtTokenProvider.createAccessToken(userA.getId());
    String tokenB = jwtTokenProvider.createAccessToken(userB.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(tokenA))
        .thenReturn(true);
    when(jwtRegistry.hasActiveJwtInformationByAccessToken(tokenB))
        .thenReturn(true);

    // when
    mockMvc.perform(
        post("/api/feeds/{feedId}/like", feedId)
            .with(csrf())
            .header("Authorization", "Bearer " + tokenA)
    ).andExpect(status().isNoContent());

    mockMvc.perform(
        post("/api/feeds/{feedId}/like", feedId)
            .with(csrf())
            .header("Authorization", "Bearer " + tokenB)
    ).andExpect(status().isNoContent());

    mockMvc.perform(
        delete("/api/feeds/{feedId}/like", feedId)
            .with(csrf())
            .header("Authorization", "Bearer " + tokenA)
    ).andExpect(status().isNoContent());

    entityManager.flush();
    entityManager.clear();

    // then
    Long totalCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feed_likes WHERE feed_id = ?",
        Long.class,
        feedId
    );

    Long userACount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feed_likes WHERE feed_id = ? AND user_id = ?",
        Long.class,
        feedId,
        userA.getId()
    );

    Long userBCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feed_likes WHERE feed_id = ? AND user_id = ?",
        Long.class,
        feedId,
        userB.getId()
    );

    assertEquals(1L, totalCount);
    assertEquals(0L, userACount);
    assertEquals(1L, userBCount);

    mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + tokenA)
                .param("limit", "10")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].likeCount").value(1))
        .andExpect(jsonPath("$.data[0].likedByMe").value(false));

    mockMvc.perform(
            get("/api/feeds")
                .header("Authorization", "Bearer " + tokenB)
                .param("limit", "10")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESCENDING")
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].likeCount").value(1))
        .andExpect(jsonPath("$.data[0].likedByMe").value(true));
  }
}
