package com.otboo.team03.feed.integration;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedDeleteIntegrationTest extends PostgresTestSupport {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ClothesRepository clothesRepository;

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
  void 피드_삭제시_연관_데이터를_정리하고_다른_피드와_의상은_유지한다()
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

    Clothes clothes = clothesRepository.saveAndFlush(
        new Clothes(
            "흰색 티셔츠",
            ClothesType.TOP,
            null,
            author
        )
    );

    Feed targetFeed = feedRepository.saveAndFlush(
        new Feed(author.getId(), weatherId, "삭제할 피드")
    );

    Feed remainingFeed = feedRepository.saveAndFlush(
        new Feed(author.getId(), weatherId, "남겨둘 피드")
    );

    insertRelations(
        targetFeed.getId(), author.getId(), clothes.getId(), now
    );

    insertRelations(
        remainingFeed.getId(), author.getId(), clothes.getId(), now
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var result = mockMvc.perform(
        delete("/api/feeds/{feedId}", targetFeed.getId())
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
    );

    entityManager.flush();
    entityManager.clear();

    // then
    result.andExpect(status().isNoContent())
        .andExpect(content().string(""));

    assertAll(
        () -> assertEquals(0L, countFeed(targetFeed.getId())),
        () -> assertEquals(0L, countRelation("feed_likes", targetFeed.getId())),
        () -> assertEquals(0L, countRelation("feed_comments", targetFeed.getId())),
        () -> assertEquals(0L, countRelation("feed_clothes", targetFeed.getId())),
        () -> assertEquals(1L, countFeed(remainingFeed.getId())),
        () -> assertEquals(1L, countRelation("feed_likes", remainingFeed.getId())),
        () -> assertEquals(1L, countRelation("feed_comments", remainingFeed.getId())),
        () -> assertEquals(1L, countRelation("feed_clothes", remainingFeed.getId())),
        () -> assertEquals(
            1L,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM clothes WHERE id = ?",
                Long.class,
                clothes.getId()
            )
        )
    );
  }

  private void insertRelations(
      UUID feedId,
      UUID authorId,
      UUID clothesId,
      Timestamp now
  ) {
    jdbcTemplate.update("""
        INSERT INTO feed_likes (id, feed_id, user_id, created_at)
        VALUES (?, ?, ?, ?)
        """,
        UUID.randomUUID(), feedId, authorId, now
    );

    jdbcTemplate.update("""
        INSERT INTO feed_comments (
            id, feed_id, author_id, content, created_at
        ) VALUES (?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(), feedId, authorId, "테스트 댓글", now
    );

    jdbcTemplate.update("""
        INSERT INTO feed_clothes (feed_id, clothes_id)
        VALUES (?, ?)
        """,
        feedId, clothesId
    );
  }

  private long countFeed(UUID feedId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feeds WHERE id = ?",
        Long.class,
        feedId
    );
  }

  private long countRelation(String table, UUID feedId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE feed_id = ?",
        Long.class,
        feedId
    );
  }

  @Test
  void 인증하지_않으면_피드를_수정하거나_삭제할_수_없다()
      throws Exception {
    // given
    UUID feedId = UUID.randomUUID();

    // when
    var updateResult = mockMvc.perform(
        patch("/api/feeds/{feedId}", feedId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
              {
                "content": "수정할 내용"
              }
              """)
    );

    var deleteResult = mockMvc.perform(
        delete("/api/feeds/{feedId}", feedId)
            .with(csrf())
    );

    // then
    updateResult.andExpect(status().isUnauthorized());
    deleteResult.andExpect(status().isUnauthorized());
  }
}
