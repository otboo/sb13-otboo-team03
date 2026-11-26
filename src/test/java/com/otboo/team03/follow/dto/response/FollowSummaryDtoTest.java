package com.otboo.team03.follow.dto.response;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowSummaryDtoTest {

  @Test
  void 팔로우_요약_정보에_팔로우_수와_관계_정보가_포함된다() {
    // given
    UUID followeeId = UUID.randomUUID();
    UUID followedByMeId = UUID.randomUUID();

    long followerCount = 10L;
    long followingCount = 5L;

    // when
    FollowSummaryDto dto = new FollowSummaryDto(
        followeeId,
        followerCount,
        followingCount,
        true,
        followedByMeId,
        false
    );

    // then
    assertAll(
        () -> assertEquals(followeeId, dto.followeeId()),
        () -> assertEquals(followerCount, dto.followerCount()),
        () -> assertEquals(followingCount, dto.followingCount()),
        () -> assertTrue(dto.followedByMe()),
        () -> assertEquals(followedByMeId, dto.followedByMeId()),
        () -> assertFalse(dto.followingMe())
    );
  }
}
