package com.otboo.team03.follow.dto.response;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserSummaryTest {

  @Test
  void 사용자_요약_정보에_ID_이름_프로필_이미지가_포함된다() {
    // given
    UUID userId = UUID.randomUUID();
    String name = "테스트 사용자";
    String profileImageUrl = "https://example.com/profile.png";

    // when
    UserSummary summary = new UserSummary(
        userId,
        name,
        profileImageUrl
    );

    // then
    assertAll(
        () -> assertEquals(userId, summary.userId()),
        () -> assertEquals(name, summary.name()),
        () -> assertEquals(profileImageUrl, summary.profileImageUrl())
    );
  }
}
