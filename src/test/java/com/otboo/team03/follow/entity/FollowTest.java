package com.otboo.team03.follow.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowTest {

  @Test
  void 팔로우를_생성하면_팔로워와_팔로우_대상_ID가_유지된다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    // when
    Follow follow = new Follow(followerId, followeeId);

    // then
    assertAll(
        () -> assertEquals(followerId, follow.getFollowerId()),
        () -> assertEquals(followeeId, follow.getFolloweeId())
    );
  }
}