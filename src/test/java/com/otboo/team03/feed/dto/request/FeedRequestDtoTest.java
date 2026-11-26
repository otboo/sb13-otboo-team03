package com.otboo.team03.feed.dto.request;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FeedRequestDtoTest {

  @Test
  void 피드_생성_요청의_필드가_정상적으로_설정된다() {
    // given
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    List<UUID> clothesIds = List.of(UUID.randomUUID());
    String content = "오늘의 코디";

    // when
    FeedCreateRequest request = new FeedCreateRequest(
        authorId, weatherId, clothesIds, content
    );

    // then
    assertAll(
        () -> assertEquals(authorId, request.authorId()),
        () -> assertEquals(weatherId, request.weatherId()),
        () -> assertEquals(clothesIds, request.clothesIds()),
        () -> assertEquals(content, request.content())
    );
  }

  @Test
  void 피드_수정_요청은_내용을_전달한다() {
    // given
    String content = "수정한 코디";

    // when
    FeedUpdateRequest request = new FeedUpdateRequest(content);

    // then
    assertEquals(content, request.content());
  }

  @Test
  void 댓글_생성_요청의_필드가_정상적으로_설정된다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    String content = "멋진 코디네요";

    // when
    CommentCreateRequest request = new CommentCreateRequest(
        feedId, authorId, content
    );

    // then
    assertAll(
        () -> assertEquals(feedId, request.feedId()),
        () -> assertEquals(authorId, request.authorId()),
        () -> assertEquals(content, request.content())
    );
  }
}
