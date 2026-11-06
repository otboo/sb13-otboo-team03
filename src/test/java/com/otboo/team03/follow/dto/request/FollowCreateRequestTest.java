package com.otboo.team03.follow.dto.request;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowCreateRequestTest {

  @Test
  void 팔로우_생성_요청에_팔로워와_대상_사용자_ID가_포함된다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    // when
    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    // then
    assertAll(
        () -> assertEquals(followerId, request.followerId()),
        () -> assertEquals(followeeId, request.followeeId())
    );
  }
}
