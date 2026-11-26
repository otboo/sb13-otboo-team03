package com.otboo.team03.follow.dto.response;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowDtoTest {

  @Test
  void 팔로우_응답에_ID와_팔로워_팔로우_대상_정보가_포함된다() {
    // given
    UUID followId = UUID.randomUUID();

    UserSummary follower = new UserSummary(
        UUID.randomUUID(),
        "팔로워",
        "https://example.com/follower.png"
    );

    UserSummary followee = new UserSummary(
        UUID.randomUUID(),
        "팔로우 대상",
        "https://example.com/followee.png"
    );

    // when
    FollowDto dto = new FollowDto(
        followId,
        followee,
        follower
    );

    // then
    assertAll(
        () -> assertEquals(followId, dto.id()),
        () -> assertEquals(followee, dto.followee()),
        () -> assertEquals(follower, dto.follower())
    );
  }
}
