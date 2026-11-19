package com.otboo.team03.feed.integration;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.dto.request.CommentCreateRequest;
import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.feed.service.FeedService;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.follow.repository.FollowRepository;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FeedNotificationOutboxIntegrationTest extends PostgresTestSupport {

  @Autowired
  private FeedService feedService;

  @Autowired
  private FeedRepository feedRepository;

  @Autowired
  private FollowRepository followRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Autowired
  private ClothesRepository clothesRepository;

  @Test
  void 피드_좋아요는_Outbox에_한번만_저장된다() {
    // given
    User author = createUser("피드 작성자");
    User liker = createUser("좋아요 사용자");

    UUID weatherId = createWeather();
    Feed feed = createFeed(author.getId(), weatherId);

    // when
    feedService.like(feed.getId(), liker.getId());
    feedService.like(feed.getId(), liker.getId());

    entityManager.flush();

    List<Map<String, Object>> events = findOutboxEvents(
        feed.getId(),
        "FEED_LIKED"
    );

    Long likeCount = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM feed_likes
        WHERE feed_id = ? AND user_id = ?
        """,
        Long.class,
        feed.getId(),
        liker.getId()
    );

    // then
    assertEquals(1L, likeCount);
    assertEquals(1, events.size());

    assertOutboxEvent(
        events.get(0),
        "FEED_LIKED",
        feed.getId(),
        author.getId(),
        liker.getId(),
        "새로운 좋아요",
        "누군가 회원님의 피드에 좋아요를 눌렀습니다."
    );
  }

  @Test
  void 피드_댓글_등록시_Outbox에_알림이_저장된다() {
    // given
    User author = createUser("피드 작성자");
    User commenter = createUser("댓글 작성자");

    UUID weatherId = createWeather();
    Feed feed = createFeed(author.getId(), weatherId);

    CommentCreateRequest request = new CommentCreateRequest(
        feed.getId(),
        commenter.getId(),
        "좋은 코디네요!"
    );

    // when
    feedService.createComment(
        feed.getId(),
        commenter.getId(),
        request
    );

    entityManager.flush();

    List<Map<String, Object>> events = findOutboxEvents(
        feed.getId(),
        "FEED_COMMENTED"
    );

    Long commentCount = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM feed_comments
        WHERE feed_id = ? AND author_id = ?
        """,
        Long.class,
        feed.getId(),
        commenter.getId()
    );

    // then
    assertEquals(1L, commentCount);
    assertEquals(1, events.size());

    assertOutboxEvent(
        events.get(0),
        "FEED_COMMENTED",
        feed.getId(),
        author.getId(),
        commenter.getId(),
        "새로운 댓글",
        "누군가 회원님의 피드에 댓글을 남겼습니다."
    );
  }

  @Test
  void 피드_등록시_팔로워별_Outbox_이벤트가_저장된다() {
    // given
    User author = createUser("피드 작성자");
    User firstFollower = createUser("첫 번째 팔로워");
    User secondFollower = createUser("두 번째 팔로워");

    UUID weatherId = createWeather();

    followRepository.saveAndFlush(
        new Follow(firstFollower.getId(), author.getId())
    );

    followRepository.saveAndFlush(
        new Follow(secondFollower.getId(), author.getId())
    );

    FeedCreateRequest request = new FeedCreateRequest(
        author.getId(),
        weatherId,
        List.of(),
        "오늘의 코디"
    );

    // when
    FeedDto result = feedService.create(request);

    entityManager.flush();

    List<Map<String, Object>> events = findOutboxEvents(
        result.id(),
        "FOLLOWING_USER_FEED_CREATED"
    );

    // then
    assertEquals(2, events.size());

    Set<String> receiverIds = events.stream()
        .map(event -> (String) event.get("receiver_id"))
        .collect(Collectors.toSet());

    Set<Object> eventIds = events.stream()
        .map(event -> event.get("event_id"))
        .collect(Collectors.toSet());

    assertEquals(
        Set.of(
            firstFollower.getId().toString(),
            secondFollower.getId().toString()
        ),
        receiverIds
    );

    assertEquals(2, eventIds.size());

    for (Map<String, Object> event : events) {
      assertOutboxEvent(
          event,
          "FOLLOWING_USER_FEED_CREATED",
          result.id(),
          UUID.fromString((String) event.get("receiver_id")),
          author.getId(),
          "새로운 피드",
          "팔로우한 사용자가 새 피드를 올렸습니다."
      );
    }
  }

  @Test
  void 피드_등록시_팔로워가_없으면_Outbox_이벤트를_저장하지_않는다() {
    // given
    User author = createUser("피드 작성자");
    UUID weatherId = createWeather();

    FeedCreateRequest request = new FeedCreateRequest(
        author.getId(),
        weatherId,
        List.of(),
        "오늘의 코디"
    );

    // when
    FeedDto result = feedService.create(request);

    entityManager.flush();

    List<Map<String, Object>> events = findOutboxEvents(
        result.id(),
        "FOLLOWING_USER_FEED_CREATED"
    );

    // then
    assertAll(
        () -> assertNotNull(result.id()),
        () -> assertTrue(feedRepository.existsById(result.id())),
        () -> assertTrue(events.isEmpty())
    );
  }

  @Test
  void 피드_좋아요_트랜잭션이_롤백되면_Outbox도_롤백된다() {
    // given
    AtomicReference<UUID> feedIdHolder = new AtomicReference<>();

    TransactionTemplate transactionTemplate =
        new TransactionTemplate(transactionManager);

    transactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW
    );

    // when
    transactionTemplate.executeWithoutResult(status -> {
      User author = createUser("피드 작성자");
      User liker = createUser("좋아요 사용자");

      UUID weatherId = createWeather();
      Feed feed = createFeed(author.getId(), weatherId);

      feedIdHolder.set(feed.getId());

      feedService.like(feed.getId(), liker.getId());

      entityManager.flush();

      assertEquals(
          1,
          findOutboxEvents(feed.getId(), "FEED_LIKED").size()
      );

      status.setRollbackOnly();
    });

    UUID feedId = feedIdHolder.get();

    Long feedCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feeds WHERE id = ?",
        Long.class,
        feedId
    );

    Long likeCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feed_likes WHERE feed_id = ?",
        Long.class,
        feedId
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

    // then
    assertAll(
        () -> assertNotNull(feedId),
        () -> assertEquals(0L, feedCount),
        () -> assertEquals(0L, likeCount),
        () -> assertEquals(0L, outboxCount)
    );
  }

  private User createUser(String name) {
    return userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            name
        )
    );
  }

  private UUID createWeather() {
    UUID weatherId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());

    jdbcTemplate.update(
        """
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
        weatherId,
        now,
        now,
        now,
        now
    );

    return weatherId;
  }

  private Feed createFeed(UUID authorId, UUID weatherId) {
    return feedRepository.saveAndFlush(
        new Feed(
            authorId,
            weatherId,
            "오늘의 코디"
        )
    );
  }

  private List<Map<String, Object>> findOutboxEvents(
      UUID aggregateId,
      String eventType
  ) {
    return jdbcTemplate.queryForList(
        """
        SELECT event_id,
               event_type,
               schema_version,
               aggregate_type,
               aggregate_id,
               partition_key,
               occurred_at,
               payload->>'receiverId' AS receiver_id,
               payload->>'actorId' AS actor_id,
               payload->>'relatedId' AS related_id,
               payload->>'title' AS title,
               payload->>'content' AS content,
               payload->>'level' AS level
        FROM outbox_events
        WHERE aggregate_id = ?
          AND event_type = ?
        """,
        aggregateId,
        eventType
    );
  }

  private void assertOutboxEvent(
      Map<String, Object> event,
      String eventType,
      UUID feedId,
      UUID receiverId,
      UUID actorId,
      String title,
      String content
  ) {
    assertAll(
        () -> assertNotNull(event.get("event_id")),
        () -> assertEquals(eventType, event.get("event_type")),
        () -> assertEquals(
            1,
            ((Number) event.get("schema_version")).intValue()
        ),
        () -> assertEquals("FEED", event.get("aggregate_type")),
        () -> assertEquals(feedId, event.get("aggregate_id")),
        () -> assertEquals(
            receiverId.toString(),
            event.get("partition_key")
        ),
        () -> assertNotNull(event.get("occurred_at")),
        () -> assertEquals(
            receiverId.toString(),
            event.get("receiver_id")
        ),
        () -> assertEquals(
            actorId.toString(),
            event.get("actor_id")
        ),
        () -> assertEquals(
            feedId.toString(),
            event.get("related_id")
        ),
        () -> assertEquals(title, event.get("title")),
        () -> assertEquals(content, event.get("content")),
        () -> assertEquals("INFO", event.get("level"))
    );
  }

  @Test
  void 피드_댓글_트랜잭션이_롤백되면_Outbox도_롤백된다() {
    // given
    AtomicReference<UUID> feedIdHolder = new AtomicReference<>();

    TransactionTemplate transactionTemplate =
        new TransactionTemplate(transactionManager);

    transactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW
    );

    // when
    transactionTemplate.executeWithoutResult(status -> {
      User author = createUser("피드 작성자");
      User commenter = createUser("댓글 작성자");

      UUID weatherId = createWeather();
      Feed feed = createFeed(author.getId(), weatherId);

      feedIdHolder.set(feed.getId());

      CommentCreateRequest request = new CommentCreateRequest(
          feed.getId(),
          commenter.getId(),
          "좋은 코디네요!"
      );

      feedService.createComment(
          feed.getId(),
          commenter.getId(),
          request
      );

      entityManager.flush();

      Long commentCount = jdbcTemplate.queryForObject(
          """
          SELECT COUNT(*)
          FROM feed_comments
          WHERE feed_id = ?
          """,
          Long.class,
          feed.getId()
      );

      assertEquals(1L, commentCount);

      assertEquals(
          1,
          findOutboxEvents(feed.getId(), "FEED_COMMENTED").size()
      );

      status.setRollbackOnly();
    });

    UUID feedId = feedIdHolder.get();

    Long commentCount = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM feed_comments
        WHERE feed_id = ?
        """,
        Long.class,
        feedId
    );

    List<Map<String, Object>> events = findOutboxEvents(
        feedId,
        "FEED_COMMENTED"
    );

    // then
    assertAll(
        () -> assertNotNull(feedId),
        () -> assertEquals(0L, commentCount),
        () -> assertTrue(events.isEmpty())
    );
  }

  @Test
  void 피드_등록_트랜잭션이_롤백되면_의상_연결과_Outbox도_롤백된다() {
    // given
    AtomicReference<UUID> feedIdHolder = new AtomicReference<>();

    TransactionTemplate transactionTemplate =
        new TransactionTemplate(transactionManager);

    transactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW
    );

    // when
    transactionTemplate.executeWithoutResult(status -> {
      User author = createUser("피드 작성자");
      User firstFollower = createUser("첫 번째 팔로워");
      User secondFollower = createUser("두 번째 팔로워");

      UUID weatherId = createWeather();

      Clothes clothes = clothesRepository.saveAndFlush(
          new Clothes(
              "흰색 티셔츠",
              ClothesType.TOP,
              null,
              author
          )
      );

      followRepository.saveAndFlush(
          new Follow(
              firstFollower.getId(),
              author.getId()
          )
      );

      followRepository.saveAndFlush(
          new Follow(
              secondFollower.getId(),
              author.getId()
          )
      );

      FeedCreateRequest request = new FeedCreateRequest(
          author.getId(),
          weatherId,
          List.of(clothes.getId()),
          "오늘의 코디"
      );

      FeedDto result = feedService.create(request);

      feedIdHolder.set(result.id());

      entityManager.flush();

      Long feedCount = jdbcTemplate.queryForObject(
          """
          SELECT COUNT(*)
          FROM feeds
          WHERE id = ?
          """,
          Long.class,
          result.id()
      );

      Long feedClothesCount = jdbcTemplate.queryForObject(
          """
          SELECT COUNT(*)
          FROM feed_clothes
          WHERE feed_id = ?
          """,
          Long.class,
          result.id()
      );

      assertAll(
          () -> assertEquals(1L, feedCount),
          () -> assertEquals(1L, feedClothesCount),
          () -> assertEquals(
              2,
              findOutboxEvents(
                  result.id(),
                  "FOLLOWING_USER_FEED_CREATED"
              ).size()
          )
      );

      status.setRollbackOnly();
    });

    UUID feedId = feedIdHolder.get();

    Long feedCount = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM feeds
        WHERE id = ?
        """,
        Long.class,
        feedId
    );

    Long feedClothesCount = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM feed_clothes
        WHERE feed_id = ?
        """,
        Long.class,
        feedId
    );

    List<Map<String, Object>> events = findOutboxEvents(
        feedId,
        "FOLLOWING_USER_FEED_CREATED"
    );

    // then
    assertAll(
        () -> assertNotNull(feedId),
        () -> assertEquals(0L, feedCount),
        () -> assertEquals(0L, feedClothesCount),
        () -> assertTrue(events.isEmpty())
    );
  }
}
