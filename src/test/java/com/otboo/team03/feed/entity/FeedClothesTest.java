package com.otboo.team03.feed.entity;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FeedClothesTest {

  @Test
  void 피드와_의상을_연결할_수_있다() {
    // given
    UUID authorId = UUID.randomUUID();

    Feed feed = new Feed(
        authorId,
        UUID.randomUUID(),
        "오늘의 코디"
    );

    Clothes clothes = new Clothes(
        "흰색 티셔츠",
        ClothesType.TOP,
        null,
        authorId
    );

    // when
    FeedClothes feedClothes = new FeedClothes(feed, clothes);

    // then
    assertAll(
        () -> assertSame(feed, feedClothes.getFeed()),
        () -> assertSame(clothes, feedClothes.getClothes())
    );
  }

  @Test
  void 동일한_피드와_의상_ID는_동일한_복합키를_가진다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();

    // when
    FeedClothes.Key first = new FeedClothes.Key(feedId, clothesId);
    FeedClothes.Key second = new FeedClothes.Key(feedId, clothesId);

    // then
    assertAll(
        () -> assertEquals(first, second),
        () -> assertEquals(first.hashCode(), second.hashCode())
    );
  }

  @Test
  void 피드나_의상이_null이면_연결할_수_없다() {
    // given
    Feed feed = new Feed(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "오늘의 코디"
    );

    Clothes clothes = new Clothes(
        "청바지",
        ClothesType.BOTTOM,
        null,
        UUID.randomUUID()
    );

    // when
    // then
    assertAll(
        () -> assertThrows(
            NullPointerException.class,
            () -> new FeedClothes(null, clothes)
        ),
        () -> assertThrows(
            NullPointerException.class,
            () -> new FeedClothes(feed, null)
        )
    );
  }
}
