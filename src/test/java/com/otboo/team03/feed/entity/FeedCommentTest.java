package com.otboo.team03.feed.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FeedCommentTest {

  @Test
  void 댓글_생성시_피드와_작성자와_내용이_설정된다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    String content = "오늘 코디 멋있네요!";

    // when
    FeedComment comment = new FeedComment(feedId, authorId, content);

    // then
    assertAll(
        () -> assertEquals(feedId, comment.getFeedId()),
        () -> assertEquals(authorId, comment.getAuthorId()),
        () -> assertEquals(content, comment.getContent())
    );
  }

  @Test
  void 피드_ID가_null이면_댓글을_생성할_수_없다() {
    // given
    UUID authorId = UUID.randomUUID();

    // when
    Executable createComment =
        () -> new FeedComment(null, authorId, "댓글 내용");

    // then
    assertThrows(NullPointerException.class, createComment);
  }

  @Test
  void 작성자_ID가_null이면_댓글을_생성할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();

    // when
    Executable createComment =
        () -> new FeedComment(feedId, null, "댓글 내용");

    // then
    assertThrows(NullPointerException.class, createComment);
  }

  @Test
  void 내용이_null이면_댓글을_생성할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    // when
    Executable createComment =
        () -> new FeedComment(feedId, authorId, null);

    // then
    assertThrows(NullPointerException.class, createComment);
  }
}
