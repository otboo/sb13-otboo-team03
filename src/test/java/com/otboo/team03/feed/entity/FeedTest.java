package com.otboo.team03.feed.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FeedTest {

  @Test
  void 피드_내용을_수정하면_작성자와_날씨는_변경되지_않는다() {
    // given
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();

    Feed feed = new Feed(
        authorId,
        weatherId,
        "수정 전 내용"
    );

    // when
    feed.updateContent("수정 후 내용");

    // then
    assertAll(
        () -> assertEquals(
            "수정 후 내용",
            feed.getContent()
        ),
        () -> assertEquals(
            authorId,
            feed.getAuthorId()
        ),
        () -> assertEquals(
            weatherId,
            feed.getWeatherId()
        )
    );
  }
}
