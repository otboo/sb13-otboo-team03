package com.otboo.team03.follow.integration;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.follow.repository.FollowRepository;
import com.otboo.team03.follow.service.FollowService;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Transactional
class FollowServiceIntegrationTest extends PostgresTestSupport {

  @Autowired
  private FollowService followService;

  @Autowired
  private FollowRepository followRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void 팔로우_생성시_DB에_저장되고_사용자_정보를_반환한다() {
    // given
    User follower = createUser("팔로워");
    User followee = createUser("팔로우 대상");

    FollowCreateRequest request = new FollowCreateRequest(
        follower.getId(),
        followee.getId()
    );

    // when
    FollowDto response = followService.create(
        follower.getId(),
        request
    );

    followRepository.flush();
    entityManager.clear();

    Follow savedFollow = followRepository.findById(response.id())
        .orElseThrow();

    // then
    assertAll(
        () -> assertNotNull(response.id()),
        () -> assertEquals(follower.getId(), savedFollow.getFollowerId()),
        () -> assertEquals(followee.getId(), savedFollow.getFolloweeId()),
        () -> assertNotNull(savedFollow.getCreatedAt()),
        () -> assertEquals(follower.getId(), response.follower().userId()),
        () -> assertEquals("팔로워", response.follower().name()),
        () -> assertEquals(followee.getId(), response.followee().userId()),
        () -> assertEquals("팔로우 대상", response.followee().name()),
        () -> assertNull(response.follower().profileImageUrl()),
        () -> assertNull(response.followee().profileImageUrl())
    );
  }

  @Test
  void 팔로우를_취소하면_해당_관계만_DB에서_삭제된다() {
    // given
    User follower = createUser("팔로워");
    User followeeA = createUser("팔로우 대상 A");
    User followeeB = createUser("팔로우 대상 B");

    FollowDto followA = followService.create(
        follower.getId(),
        new FollowCreateRequest(
            follower.getId(),
            followeeA.getId()
        )
    );

    FollowDto followB = followService.create(
        follower.getId(),
        new FollowCreateRequest(
            follower.getId(),
            followeeB.getId()
        )
    );

    followRepository.flush();
    entityManager.clear();

    // when
    followService.delete(followA.id(), follower.getId());

    followRepository.flush();
    entityManager.clear();

    // then
    assertAll(
        () -> assertFalse(followRepository.existsById(followA.id())),
        () -> assertTrue(followRepository.existsById(followB.id())),
        () -> assertFalse(
            followRepository.existsByFollowerIdAndFolloweeId(
                follower.getId(),
                followeeA.getId()
            )
        ),
        () -> assertTrue(
            followRepository.existsByFollowerIdAndFolloweeId(
                follower.getId(),
                followeeB.getId()
            )
        )
    );
  }

  @Test
  void 다른_사용자는_팔로우_관계를_삭제할_수_없다() {
    // given
    User follower = createUser("팔로워");
    User followee = createUser("팔로우 대상");
    User otherUser = createUser("다른 사용자");

    FollowDto response = followService.create(
        follower.getId(),
        new FollowCreateRequest(
            follower.getId(),
            followee.getId()
        )
    );

    followRepository.flush();
    entityManager.clear();

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.delete(
            response.id(),
            otherUser.getId()
        )
    );

    // then
    assertAll(
        () -> assertEquals(
            ErrorCode.ACCESS_DENIED,
            exception.getErrorCode()
        ),
        () -> assertTrue(
            followRepository.existsById(response.id())
        )
    );
  }

  @Test
  void 팔로워_이름_검색과_커서_페이지네이션이_DB에서_동작한다() {
    // given
    User followee = createUser("팔로우 대상");
    User otherFollowee = createUser("다른 대상");

    User minsu = createUser("김민수");
    User minji = createUser("이민지");
    User seojun = createUser("박서준");
    User minho = createUser("최민호");

    followRepository.saveAndFlush(
        new Follow(minsu.getId(), followee.getId())
    );
    followRepository.saveAndFlush(
        new Follow(minji.getId(), followee.getId())
    );
    followRepository.saveAndFlush(
        new Follow(seojun.getId(), followee.getId())
    );
    followRepository.saveAndFlush(
        new Follow(minho.getId(), followee.getId())
    );
    followRepository.saveAndFlush(
        new Follow(minsu.getId(), otherFollowee.getId())
    );

    entityManager.clear();

    // when
    CursorResponse<FollowDto> firstPage = followService.getFollowers(
        followee.getId(),
        2,
        null,
        null,
        "민"
    );

    CursorResponse<FollowDto> secondPage = followService.getFollowers(
        followee.getId(),
        2,
        firstPage.nextCursor(),
        firstPage.nextIdAfter(),
        "민"
    );

    List<FollowDto> all = Stream.concat(
        firstPage.data().stream(),
        secondPage.data().stream()
    ).toList();

    Set<String> names = Set.copyOf(
        all.stream()
            .map(dto -> dto.follower().name())
            .toList()
    );

    // then
    assertAll(
        () -> assertEquals(2, firstPage.data().size()),
        () -> assertEquals(1, secondPage.data().size()),
        () -> assertTrue(firstPage.hasNext()),
        () -> assertFalse(secondPage.hasNext()),
        () -> assertNotNull(firstPage.nextCursor()),
        () -> assertNotNull(firstPage.nextIdAfter()),
        () -> assertNull(secondPage.nextCursor()),
        () -> assertNull(secondPage.nextIdAfter()),
        () -> assertEquals(3L, firstPage.totalCount()),
        () -> assertEquals(3L, secondPage.totalCount()),
        () -> assertEquals(
            Set.of("김민수", "이민지", "최민호"),
            names
        ),
        () -> assertTrue(
            all.stream().allMatch(
                dto -> followee.getId().equals(
                    dto.followee().userId()
                )
            )
        ),
        () -> assertEquals("createdAt", firstPage.sortBy()),
        () -> assertEquals("DESCENDING", firstPage.sortDirection())
    );
  }

  @Test
  void 동일_생성시각의_팔로워도_커서로_중복_누락없이_조회한다() {
    // given
    User followee = createUser("팔로우 대상");
    User otherFollowee = createUser("다른 대상");

    User firstFollower = createUser("팔로워 A");
    User secondFollower = createUser("팔로워 B");
    User thirdFollower = createUser("팔로워 C");
    User fourthFollower = createUser("팔로워 D");
    User fifthFollower = createUser("팔로워 E");

    Instant sameTime = Instant.parse("2026-11-09T03:00:00Z");
    Instant olderTime = Instant.parse("2026-11-09T02:00:00Z");

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

    insertPaginationFollow(
        id3, thirdFollower.getId(), followee.getId(), sameTime
    );
    insertPaginationFollow(
        id5, firstFollower.getId(), followee.getId(), sameTime
    );
    insertPaginationFollow(
        id2, fourthFollower.getId(), followee.getId(), sameTime
    );
    insertPaginationFollow(
        id4, secondFollower.getId(), followee.getId(), sameTime
    );
    insertPaginationFollow(
        id1, fifthFollower.getId(), followee.getId(), olderTime
    );
    insertPaginationFollow(
        otherId, firstFollower.getId(), otherFollowee.getId(), sameTime
    );

    entityManager.clear();

    // when
    CursorResponse<FollowDto> firstPage = followService.getFollowers(
        followee.getId(), 2, null, null, null
    );

    CursorResponse<FollowDto> secondPage = followService.getFollowers(
        followee.getId(),
        2,
        firstPage.nextCursor(),
        firstPage.nextIdAfter(),
        null
    );

    CursorResponse<FollowDto> thirdPage = followService.getFollowers(
        followee.getId(),
        2,
        secondPage.nextCursor(),
        secondPage.nextIdAfter(),
        null
    );

    CursorResponse<FollowDto> emptyPage = followService.getFollowers(
        followee.getId(),
        2,
        olderTime.toString(),
        id1,
        null
    );

    // then
    assertAll(
        () -> assertEquals(2, firstPage.data().size()),
        () -> assertEquals(id5, firstPage.data().get(0).id()),
        () -> assertEquals(id4, firstPage.data().get(1).id()),
        () -> assertEquals(sameTime.toString(), firstPage.nextCursor()),
        () -> assertEquals(id4, firstPage.nextIdAfter()),
        () -> assertTrue(firstPage.hasNext()),
        () -> assertEquals(5L, firstPage.totalCount()),

        () -> assertEquals(2, secondPage.data().size()),
        () -> assertEquals(id3, secondPage.data().get(0).id()),
        () -> assertEquals(id2, secondPage.data().get(1).id()),
        () -> assertEquals(sameTime.toString(), secondPage.nextCursor()),
        () -> assertEquals(id2, secondPage.nextIdAfter()),
        () -> assertTrue(secondPage.hasNext()),
        () -> assertEquals(5L, secondPage.totalCount()),

        () -> assertEquals(1, thirdPage.data().size()),
        () -> assertEquals(id1, thirdPage.data().get(0).id()),
        () -> assertFalse(thirdPage.hasNext()),
        () -> assertNull(thirdPage.nextCursor()),
        () -> assertNull(thirdPage.nextIdAfter()),
        () -> assertEquals(5L, thirdPage.totalCount()),

        () -> assertTrue(emptyPage.data().isEmpty()),
        () -> assertFalse(emptyPage.hasNext()),
        () -> assertEquals(5L, emptyPage.totalCount())
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

  private void insertPaginationFollow(
      UUID followId,
      UUID followerId,
      UUID followeeId,
      Instant createdAt
  ) {
    jdbcTemplate.update("""
        INSERT INTO follows (
            id, follower_id, followee_id, created_at
        ) VALUES (?, ?, ?, ?)
        """,
        followId,
        followerId,
        followeeId,
        Timestamp.from(createdAt)
    );
  }

  @Test
  void 팔로워_목록_크기가_증가해도_SQL_조회_횟수는_일정하다() {
    // given
    User followee = createUser("팔로우 대상");

    for (int i = 0; i < 5; i++) {
      User follower = createUser("팔로워 " + i);

      followRepository.saveAndFlush(
          new Follow(follower.getId(), followee.getId())
      );
    }

    entityManager.clear();

    SessionFactory sessionFactory = entityManager
        .getEntityManagerFactory()
        .unwrap(SessionFactory.class);

    Statistics statistics = sessionFactory.getStatistics();
    boolean previousEnabled = statistics.isStatisticsEnabled();

    statistics.setStatisticsEnabled(true);

    try {
      // when
      statistics.clear();

      CursorResponse<FollowDto> smallPage = followService.getFollowers(
          followee.getId(), 2, null, null, null
      );

      long smallPageQueries = statistics.getPrepareStatementCount();

      entityManager.clear();
      statistics.clear();

      CursorResponse<FollowDto> largePage = followService.getFollowers(
          followee.getId(), 5, null, null, null
      );

      long largePageQueries = statistics.getPrepareStatementCount();

      // then
      assertAll(
          () -> assertEquals(2, smallPage.data().size()),
          () -> assertEquals(5, largePage.data().size()),
          () -> assertEquals(smallPageQueries, largePageQueries),
          () -> assertEquals(4L, largePageQueries)
      );
    } finally {
      statistics.setStatisticsEnabled(previousEnabled);
    }
  }
}
