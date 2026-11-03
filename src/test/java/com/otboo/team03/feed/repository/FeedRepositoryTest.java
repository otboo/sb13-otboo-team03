package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FeedRepositoryTest {

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EntityManager entityManager;

  @Test
  void 피드를_저장하고_ID로_조회할_수_있다() {
    // given
    User author = userRepository.save(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "테스트 사용자"
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
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    Feed feed = new Feed(
        author.getId(),
        weatherId,
        "오늘의 코디"
    );

    // when
    Feed savedFeed = feedRepository.saveAndFlush(feed);
    UUID feedId = savedFeed.getId();

    entityManager.clear();

    Feed foundFeed = feedRepository.findById(feedId)
        .orElseThrow();

    // then
    assertAll(
        () -> assertEquals(feedId, foundFeed.getId()),
        () -> assertEquals(author.getId(), foundFeed.getAuthorId()),
        () -> assertEquals(weatherId, foundFeed.getWeatherId()),
        () -> assertEquals("오늘의 코디", foundFeed.getContent()),
        () -> assertNotNull(foundFeed.getCreatedAt()),
        () -> assertNotNull(foundFeed.getUpdatedAt())
    );
  }
}
