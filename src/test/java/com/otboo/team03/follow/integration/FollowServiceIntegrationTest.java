package com.otboo.team03.follow.integration;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.follow.repository.FollowRepository;
import com.otboo.team03.follow.service.FollowService;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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

  private User createUser(String name) {
    return userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            name
        )
    );
  }
}
