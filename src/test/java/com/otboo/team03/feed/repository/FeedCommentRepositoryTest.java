package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedComment;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FeedCommentRepositoryTest extends PostgresTestSupport {

  @Autowired
  private FeedCommentRepository feedCommentRepository;

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void 댓글_커서_조회시_동일_시각_UUID_경계와_피드_구분을_유지한다() {
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
        new Feed(author.getId(), weatherId, "테스트 피드")
    );

    Feed otherFeed = feedRepository.saveAndFlush(
        new Feed(author.getId(), weatherId, "다른 피드")
    );

    Instant cursorTime = Instant.parse("2026-11-05T03:00:00Z");
    Instant olderTime = Instant.parse("2026-11-05T02:00:00Z");

    UUID firstId = UUID.fromString(
        "00000000-0000-0000-0000-000000000005"
    );
    UUID cursorId = UUID.fromString(
        "00000000-0000-0000-0000-000000000004"
    );
    UUID nextFirstId = UUID.fromString(
        "00000000-0000-0000-0000-000000000003"
    );
    UUID nextSecondId = UUID.fromString(
        "00000000-0000-0000-0000-000000000002"
    );
    UUID olderId = UUID.fromString(
        "00000000-0000-0000-0000-000000000001"
    );
    UUID otherFeedCommentId = UUID.fromString(
        "00000000-0000-0000-0000-000000000006"
    );

    insertComment(firstId, feed.getId(), author.getId(), cursorTime);
    insertComment(cursorId, feed.getId(), author.getId(), cursorTime);
    insertComment(nextFirstId, feed.getId(), author.getId(), cursorTime);
    insertComment(nextSecondId, feed.getId(), author.getId(), cursorTime);
    insertComment(olderId, feed.getId(), author.getId(), olderTime);

    insertComment(
        otherFeedCommentId,
        otherFeed.getId(),
        author.getId(),
        cursorTime
    );

    // when
    List<FeedComment> firstPage =
        feedCommentRepository.findByFeedIdOrderByCreatedAtDescIdDesc(
            feed.getId(),
            PageRequest.of(0, 2)
        );

    List<FeedComment> secondPage = feedCommentRepository.findNextPage(
        feed.getId(),
        cursorTime,
        cursorId,
        2
    );

    List<FeedComment> lastPage = feedCommentRepository.findNextPage(
        feed.getId(),
        cursorTime,
        nextSecondId,
        2
    );

    List<FeedComment> emptyPage = feedCommentRepository.findNextPage(
        feed.getId(),
        olderTime,
        olderId,
        2
    );

    // then
    assertAll(
        () -> assertEquals(
            List.of(firstId, cursorId),
            firstPage.stream().map(FeedComment::getId).toList()
        ),
        () -> assertEquals(
            List.of(nextFirstId, nextSecondId),
            secondPage.stream().map(FeedComment::getId).toList()
        ),
        () -> assertEquals(
            List.of(olderId),
            lastPage.stream().map(FeedComment::getId).toList()
        ),
        () -> assertTrue(emptyPage.isEmpty()),
        () -> assertEquals(
            5L,
            feedCommentRepository.countByFeedId(feed.getId())
        )
    );
  }

  private void insertComment(
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
