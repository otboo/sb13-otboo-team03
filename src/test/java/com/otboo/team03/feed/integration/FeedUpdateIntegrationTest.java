package com.otboo.team03.feed.integration;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedClothes;
import com.otboo.team03.feed.repository.FeedClothesRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeedUpdateIntegrationTest extends PostgresTestSupport {

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

  @Autowired
  private ClothesRepository clothesRepository;

  @Autowired
  private FeedClothesRepository feedClothesRepository;

  @Test
  void 작성자가_피드를_수정하면_DB에_반영된다() throws Exception {
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

    Feed feed = feedRepository.saveAndFlush(
        new Feed(
            author.getId(),
            weatherId,
            "수정 전 내용"
        )
    );

    Clothes clothes = clothesRepository.saveAndFlush(
        new Clothes(
            "검정 재킷",
            ClothesType.OUTER,
            "/images/jacket.png",
            author
        )
    );

    feedClothesRepository.saveAndFlush(
        new FeedClothes(feed, clothes)
    );

    UUID definitionId = UUID.randomUUID();

    jdbcTemplate.update("""
      INSERT INTO clothes_attribute_definitions (
          id, name, selectable_values, created_at
      ) VALUES (?, ?, ARRAY['검정', '흰색']::text[], ?)
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
        "검정"
    );

    String accessToken =
        jwtTokenProvider.createAccessToken(author.getId());

    when(jwtRegistry.hasActiveJwtInformationByAccessToken(accessToken))
        .thenReturn(true);

    // when
    var result = mockMvc.perform(
        patch("/api/feeds/{feedId}", feed.getId())
            .with(csrf())
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
              {
                "content": "수정 후 내용"
              }
              """)
    );

    entityManager.flush();
    entityManager.clear();

    String savedContent = jdbcTemplate.queryForObject(
        "SELECT content FROM feeds WHERE id = ?",
        String.class,
        feed.getId()
    );

    // then
    result.andExpect(status().isOk())
        .andExpect(jsonPath("$.id")
            .value(feed.getId().toString()))
        .andExpect(jsonPath("$.content")
            .value("수정 후 내용"))
        .andExpect(jsonPath("$.author.userId")
            .value(author.getId().toString()))
        .andExpect(jsonPath("$.author.name")
            .value("피드 작성자"))
        .andExpect(jsonPath("$.weather.weatherId")
            .value(weatherId.toString()))
        .andExpect(jsonPath("$.weather.skyStatus")
            .value("CLEAR"))
        .andExpect(jsonPath("$.ootds[0].clothesId")
            .value(clothes.getId().toString()))
        .andExpect(jsonPath("$.ootds[0].name")
            .value("검정 재킷"))
        .andExpect(jsonPath("$.ootds[0].type")
            .value("OUTER"))
        .andExpect(jsonPath("$.ootds[0].attributes[0].definitionName")
            .value("색상"))
        .andExpect(jsonPath("$.ootds[0].attributes[0].value")
            .value("검정"))
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.commentCount").value(0))
        .andExpect(jsonPath("$.likedByMe").value(false));

    assertEquals("수정 후 내용", savedContent);
  }
}
