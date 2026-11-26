package com.otboo.team03.feed.integration;

import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.repository.FeedLikeRepository;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.feed.service.FeedService;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest
class FeedLikeConcurrencyIntegrationTest extends PostgresTestSupport {

  @Autowired
  private FeedService feedService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockitoSpyBean
  private FeedLikeRepository feedLikeRepository;

  @Test
  void 동일_사용자의_동시_좋아요는_한_건만_저장된다() throws Exception {
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
        new Feed(author.getId(), weatherId, "동시성 테스트")
    );

    UUID feedId = feed.getId();
    UUID likerId = liker.getId();

    CountDownLatch bothReady = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    doAnswer(invocation -> {
      bothReady.countDown();

      if (!bothReady.await(10, TimeUnit.SECONDS)) {
        throw new AssertionError("두 요청의 좋아요 저장 동기화 실패");
      }

      return mockingDetails(invocation.getMock())
          .getMockCreationSettings()
          .getDefaultAnswer()
          .answer(invocation);
    }).when(feedLikeRepository).insertIfAbsent(
        any(UUID.class),
        eq(feedId),
        eq(likerId),
        any(Instant.class)
    );

    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      // when
      Future<?> first = executor.submit(() -> {
        start.await();
        feedService.like(feedId, likerId);
        return null;
      });

      Future<?> second = executor.submit(() -> {
        start.await();
        feedService.like(feedId, likerId);
        return null;
      });

      start.countDown();

      first.get(15, TimeUnit.SECONDS);
      second.get(15, TimeUnit.SECONDS);

      // then
      Long savedCount = jdbcTemplate.queryForObject(
          """
          SELECT COUNT(*)
          FROM feed_likes
          WHERE feed_id = ? AND user_id = ?
          """,
          Long.class,
          feedId,
          likerId
      );

      Long outboxCount = jdbcTemplate.queryForObject(
          """
          SELECT COUNT(*)
          FROM outbox_events
          WHERE aggregate_id = ?
            AND event_type = 'FEED_LIKED'
          """,
          Long.class,
          feedId
      );

      assertEquals(0L, bothReady.getCount());
      assertEquals(1L, savedCount);
      assertEquals(1L, outboxCount);
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(10, TimeUnit.SECONDS);

      jdbcTemplate.update(
          "DELETE FROM outbox_events WHERE aggregate_id = ?",
          feedId
      );
      jdbcTemplate.update(
          "DELETE FROM feed_likes WHERE feed_id = ?", feedId
      );
      jdbcTemplate.update(
          "DELETE FROM feeds WHERE id = ?", feedId
      );
      jdbcTemplate.update(
          "DELETE FROM weathers WHERE id = ?", weatherId
      );
      jdbcTemplate.update(
          "DELETE FROM profiles WHERE user_id IN (?, ?)",
          author.getId(), likerId
      );
      jdbcTemplate.update(
          "DELETE FROM users WHERE id IN (?, ?)",
          author.getId(), likerId
      );
    }
  }
}
