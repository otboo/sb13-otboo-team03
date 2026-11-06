package com.otboo.team03.follow.repository;

import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.support.PostgresTestSupport;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FollowRepositoryTest extends PostgresTestSupport {

  @Autowired
  private FollowRepository followRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private EntityManager entityManager;

  @Test
  void 팔로우를_저장하고_ID로_조회할_수_있다() {
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

    Follow follow = new Follow(
        follower.getId(),
        followee.getId()
    );

    // when
    Follow savedFollow = followRepository.saveAndFlush(follow);
    UUID followId = savedFollow.getId();

    entityManager.clear();

    Follow foundFollow = followRepository.findById(followId)
        .orElseThrow();

    // then
    assertAll(
        () -> assertEquals(followId, foundFollow.getId()),
        () -> assertEquals(follower.getId(), foundFollow.getFollowerId()),
        () -> assertEquals(followee.getId(), foundFollow.getFolloweeId()),
        () -> assertNotNull(foundFollow.getCreatedAt())
    );
  }

  @Test
  void 동일한_사용자를_중복_팔로우하면_DB_제약조건으로_실패한다() {
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

    followRepository.saveAndFlush(
        new Follow(follower.getId(), followee.getId())
    );

    // when
    DataIntegrityViolationException exception = assertThrows(
        DataIntegrityViolationException.class,
        () -> followRepository.saveAndFlush(
            new Follow(follower.getId(), followee.getId())
        )
    );

    // then
    assertNotNull(exception);
  }

  @Test
  void 자기_자신을_팔로우하면_DB_제약조건으로_실패한다() {
    // given
    User user = userRepository.saveAndFlush(
        new User(
            UUID.randomUUID() + "@example.com",
            "encodedPassword",
            "테스트 사용자"
        )
    );

    Follow follow = new Follow(user.getId(), user.getId());

    // when
    DataIntegrityViolationException exception = assertThrows(
        DataIntegrityViolationException.class,
        () -> followRepository.saveAndFlush(follow)
    );

    // then
    assertNotNull(exception);
  }
}
