package com.otboo.team03.feed.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FeedLikeTest {

  @Test
  void 좋아요_생성시_피드와_사용자_ID가_설정된다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    // when
    FeedLike feedLike = new FeedLike(feedId, userId);

    // then
    assertAll(
        () -> assertEquals(feedId, feedLike.getFeedId()),
        () -> assertEquals(userId, feedLike.getUserId())
    );
  }

  @Test
  void 피드_ID가_null이면_좋아요를_생성할_수_없다() {
    // given
    UUID userId = UUID.randomUUID();

    // when
    NullPointerException exception = assertThrows(
        NullPointerException.class,
        () -> new FeedLike(null, userId)
    );

    // then
    assertNotNull(exception);
  }

  @Test
  void 사용자_ID가_null이면_좋아요를_생성할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();

    // when
    NullPointerException exception = assertThrows(
        NullPointerException.class,
        () -> new FeedLike(feedId, null)
    );

    // then
    assertNotNull(exception);
  }
}
