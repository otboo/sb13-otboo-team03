package com.otboo.team03.follow.integration;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.service.FollowService;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
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
class FollowNotificationOutboxIntegrationTest extends PostgresTestSupport {

  @Autowired
  private FollowService followService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Test
  void 팔로우_생성시_Outbox에_알림_이벤트가_저장된다() {
    // given
    User follower = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "팔로워"
        )
    );

    User followee = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "팔로우 대상"
        )
    );

    FollowCreateRequest request = new FollowCreateRequest(
        follower.getId(),
        followee.getId()
    );

    // when
    FollowDto result = followService.create(
        follower.getId(),
        request
    );

    entityManager.flush();

    List<Map<String, Object>> events = jdbcTemplate.queryForList(
        """
        SELECT event_id,
               event_type,
               schema_version,
               aggregate_type,
               aggregate_id,
               partition_key,
               status,
               payload->>'receiverId' AS receiver_id,
               payload->>'actorId' AS actor_id,
               payload->>'relatedId' AS related_id,
               payload->>'title' AS title,
               payload->>'content' AS content
        FROM outbox_events
        WHERE aggregate_id = ?
          AND event_type = 'USER_FOLLOWED'
        """,
        result.id()
    );

    // then
    assertEquals(1, events.size());

    Map<String, Object> event = events.get(0);

    assertAll(
        () -> assertNotNull(event.get("event_id")),
        () -> assertEquals("USER_FOLLOWED", event.get("event_type")),
        () -> assertEquals(
            1,
            ((Number) event.get("schema_version")).intValue()
        ),
        () -> assertEquals("FOLLOW", event.get("aggregate_type")),
        () -> assertEquals(result.id(), event.get("aggregate_id")),
        () -> assertEquals(
            followee.getId().toString(),
            event.get("partition_key")
        ),
        () -> assertEquals("PENDING", event.get("status")),
        () -> assertEquals(
            followee.getId().toString(),
            event.get("receiver_id")
        ),
        () -> assertEquals(
            follower.getId().toString(),
            event.get("actor_id")
        ),
        () -> assertEquals(
            result.id().toString(),
            event.get("related_id")
        ),
        () -> assertEquals("새로운 팔로워", event.get("title")),
        () -> assertEquals(
            "누군가 회원님을 팔로우하기 시작했습니다.",
            event.get("content")
        )
    );
  }

  @Test
  void 팔로우_트랜잭션이_롤백되면_Outbox_이벤트도_롤백된다() {
    // given
    AtomicReference<UUID> createdFollowId = new AtomicReference<>();

    TransactionTemplate transactionTemplate =
        new TransactionTemplate(transactionManager);

    transactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW
    );

    // when
    transactionTemplate.executeWithoutResult(status -> {
      User follower = userRepository.saveAndFlush(
          new User(
              UUID.randomUUID() + "@example.com",
              "encodedPassword",
              "팔로워"
          )
      );

      User followee = userRepository.saveAndFlush(
          new User(
              UUID.randomUUID() + "@example.com",
              "encodedPassword",
              "팔로우 대상"
          )
      );

      FollowDto result = followService.create(
          follower.getId(),
          new FollowCreateRequest(
              follower.getId(),
              followee.getId()
          )
      );

      createdFollowId.set(result.id());

      entityManager.flush();

      Long eventCount = jdbcTemplate.queryForObject(
          """
          SELECT COUNT(*)
          FROM outbox_events
          WHERE aggregate_id = ?
            AND event_type = 'USER_FOLLOWED'
          """,
          Long.class,
          result.id()
      );

      assertEquals(1L, eventCount);

      status.setRollbackOnly();
    });

    UUID followId = createdFollowId.get();

    Long savedFollowCount = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM follows
        WHERE id = ?
        """,
        Long.class,
        followId
    );

    Long savedOutboxCount = jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*)
        FROM outbox_events
        WHERE aggregate_id = ?
          AND event_type = 'USER_FOLLOWED'
        """,
        Long.class,
        followId
    );

    // then
    assertAll(
        () -> assertNotNull(followId),
        () -> assertEquals(0L, savedFollowCount),
        () -> assertEquals(0L, savedOutboxCount)
    );
  }
}
