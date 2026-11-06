package com.otboo.team03.feed.integration;

import com.jayway.jsonpath.JsonPath;
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

  @Test
  void 댓글_커서_페이지네이션에서_중복과_누락_없이_모든_댓글을_조회한다()
      throws Exception {
    // given
    User author = userRepository.saveAndFlush(
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
        new Feed(author.getId(), weatherId, "댓글 테스트 피드")
    );

    Feed otherFeed = feedRepository.saveAndFlush(
        new Feed(author.getId(), weatherId, "다른 피드")
    );

    Instant sameTime = Instant.parse("2026-11-05T03:00:00Z");
    Instant olderTime = Instant.parse("2026-11-05T02:00:00Z");

    UUID id5 = UUID.fromString(
        "00000000-0000-0000-0000-000000000005"
    );
    UUID id4 = UUID.fromString(
        "00000000-0000-0000-0000-000000000004"
    );
    UUID id3 = UUID.fromString(
        "00000000-0000-0000-0000-000000000003"
    );
    UUID id2 = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );
    UUID id1 = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    UUID otherId = UUID.fromString(
        "00000000-0000-0000-0000-000000000006"
    );

    insertPaginationComment(
        id5, feed.getId(), author.getId(), sameTime
    );
    insertPaginationComment(
        id4, feed.getId(), author.getId(), sameTime
    );
    insertPaginationComment(
        id3, feed.getId(), author.getId(), sameTime
    );
    insertPaginationComment(
        id2, feed.getId(), author.getId(), sameTime
    );
    insertPaginationComment(
        id1, feed.getId(), author.getId(), olderTime
    );
    insertPaginationComment(
        otherId, otherFeed.getId(), author.getId(), sameTime
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var firstPage = mockMvc.perform(
        get("/api/feeds/{feedId}/comments", feed.getId())
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
    );

    // then
    firstPage.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(id5.toString()))
        .andExpect(jsonPath("$.data[1].id")
            .value(id4.toString()))
        .andExpect(jsonPath("$.nextCursor")
            .value(sameTime.toString()))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(id4.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(5))
        .andExpect(jsonPath("$.sortBy").value("createdAt"))
        .andExpect(jsonPath("$.sortDirection")
            .value("DESCENDING"));

    String firstCursor = JsonPath.read(
        firstPage.andReturn().getResponse().getContentAsString(),
        "$.nextCursor"
    );

    String firstIdAfter = JsonPath.read(
        firstPage.andReturn().getResponse().getContentAsString(),
        "$.nextIdAfter"
    );

    // when
    var secondPage = mockMvc.perform(
        get("/api/feeds/{feedId}/comments", feed.getId())
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("cursor", firstCursor)
            .param("idAfter", firstIdAfter)
    );

    // then
    secondPage.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].id")
            .value(id3.toString()))
        .andExpect(jsonPath("$.data[1].id")
            .value(id2.toString()))
        .andExpect(jsonPath("$.nextCursor")
            .value(sameTime.toString()))
        .andExpect(jsonPath("$.nextIdAfter")
            .value(id2.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(5));

    String secondCursor = JsonPath.read(
        secondPage.andReturn().getResponse().getContentAsString(),
        "$.nextCursor"
    );

    String secondIdAfter = JsonPath.read(
        secondPage.andReturn().getResponse().getContentAsString(),
        "$.nextIdAfter"
    );

    // when
    var thirdPage = mockMvc.perform(
        get("/api/feeds/{feedId}/comments", feed.getId())
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("cursor", secondCursor)
            .param("idAfter", secondIdAfter)
    );

    // then
    thirdPage.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id")
            .value(id1.toString()))
        .andExpect(jsonPath("$.data[0].createdAt")
            .value(olderTime.toString()))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.nextCursor").isEmpty())
        .andExpect(jsonPath("$.nextIdAfter").isEmpty())
        .andExpect(jsonPath("$.totalCount").value(5));

    // when
    var emptyPage = mockMvc.perform(
        get("/api/feeds/{feedId}/comments", feed.getId())
            .header("Authorization", "Bearer " + accessToken)
            .param("limit", "2")
            .param("cursor", olderTime.toString())
            .param("idAfter", id1.toString())
    );

    // then
    emptyPage.andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(5));
  }

  private void insertPaginationComment(
      UUID commentId,
      UUID feedId,
      UUID authorId,
      Instant createdAt
  ) {
    jdbcTemplate.update("""
      INSERT INTO feed_comments (
          id, feed_id, author_id,
          content, created_at
      ) VALUES (?, ?, ?, ?, ?)
      """,
        commentId,
        feedId,
        authorId,
        "테스트 댓글",
        Timestamp.from(createdAt)
    );
  }
}
