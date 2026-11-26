package com.otboo.team03.follow.dto.response;

import com.otboo.team03.global.dto.CursorResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FollowListResponseTest {

  @Test
  void 팔로우_목록_응답에_사용자_정보와_페이지네이션_정보가_포함된다() {
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
        null
    );

    FollowDto followDto = new FollowDto(
        followId,
        followee,
        follower
    );

    String nextCursor = "2026-11-06T05:00:00Z";
    UUID nextIdAfter = followId;

    // when
    CursorResponse<FollowDto> response = new CursorResponse<>(
        List.of(followDto),
        nextCursor,
        nextIdAfter,
        true,
        10L,
        "createdAt",
        "DESCENDING"
    );

    // then
    assertAll(
        () -> assertEquals(1, response.data().size()),
        () -> assertEquals(followId, response.data().get(0).id()),
        () -> assertEquals(followee, response.data().get(0).followee()),
        () -> assertEquals(follower, response.data().get(0).follower()),
        () -> assertEquals(nextCursor, response.nextCursor()),
        () -> assertEquals(nextIdAfter, response.nextIdAfter()),
        () -> assertTrue(response.hasNext()),
        () -> assertEquals(10L, response.totalCount()),
        () -> assertEquals("createdAt", response.sortBy()),
        () -> assertEquals("DESCENDING", response.sortDirection())
    );
  }

  @Test
  void 팔로우_목록이_비어있으면_다음_페이지가_없다() {
    // given
    List<FollowDto> follows = List.of();

    // when
    CursorResponse<FollowDto> response = new CursorResponse<>(
        follows,
        null,
        null,
        false,
        0L,
        "createdAt",
        "DESCENDING"
    );

    // then
    assertAll(
        () -> assertTrue(response.data().isEmpty()),
        () -> assertNull(response.nextCursor()),
        () -> assertNull(response.nextIdAfter()),
        () -> assertFalse(response.hasNext()),
        () -> assertEquals(0L, response.totalCount())
    );
  }
}
