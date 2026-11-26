package com.otboo.team03.feed.dto.response;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.entity.ClothesType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FeedResponseDtoTest {

  @Test
  void 피드_응답은_작성자_의상_날씨_정보를_포함한다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-10-28T01:00:00Z");

    AuthorDto author = new AuthorDto(
        authorId, "테스트 사용자", "profile.jpg"
    );

    ClothesAttributeWithDefDto attribute =
        new ClothesAttributeWithDefDto(
            UUID.randomUUID(),
            "색상",
            List.of("흰색", "검정색"),
            "흰색"
        );

    OotdDto ootd = new OotdDto(
        clothesId,
        "흰색 티셔츠",
        "clothes.jpg",
        ClothesType.TOP,
        List.of(attribute)
    );

    PrecipitationDto precipitation = new PrecipitationDto(
        "RAIN", 1.5, 0.7
    );

    TemperatureDto temperature = new TemperatureDto(
        20.0, -1.0, 15.0, 23.0
    );

    WeatherSummaryDto weather = new WeatherSummaryDto(
        weatherId,
        "CLOUDY",
        precipitation,
        temperature
    );

    // when
    FeedDto feed = new FeedDto(
        feedId,
        createdAt,
        createdAt,
        author,
        weather,
        List.of(ootd),
        "오늘의 코디",
        5L,
        2,
        true
    );

    // then
    assertAll(
        () -> assertEquals(feedId, feed.id()),
        () -> assertEquals(createdAt, feed.createdAt()),
        () -> assertEquals(authorId, feed.author().userId()),
        () -> assertEquals(clothesId, feed.ootds().get(0).clothesId()),
        () -> assertEquals("흰색", feed.ootds().get(0)
            .attributes().get(0).value()),
        () -> assertEquals(weatherId, feed.weather().weatherId()),
        () -> assertEquals("RAIN", feed.weather().precipitation().type()),
        () -> assertEquals(20.0, feed.weather().temperature().current()),
        () -> assertEquals(5L, feed.likeCount()),
        () -> assertEquals(2, feed.commentCount()),
        () -> assertTrue(feed.likedByMe())
    );
  }

  @Test
  void 댓글_응답은_작성자와_내용을_포함한다() {
    // given
    UUID commentId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-10-28T02:00:00Z");

    AuthorDto author = new AuthorDto(
        authorId, "댓글 작성자", null
    );

    // when
    CommentDto comment = new CommentDto(
        commentId,
        createdAt,
        feedId,
        author,
        "멋진 코디네요"
    );

    // then
    assertAll(
        () -> assertEquals(commentId, comment.id()),
        () -> assertEquals(createdAt, comment.createdAt()),
        () -> assertEquals(feedId, comment.feedId()),
        () -> assertEquals(authorId, comment.author().userId()),
        () -> assertEquals("멋진 코디네요", comment.content())
    );
  }
}
