package com.otboo.team03.feed.mapper;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.feed.dto.response.AuthorDto;
import com.otboo.team03.feed.dto.response.CommentDto;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.dto.response.PrecipitationDto;
import com.otboo.team03.feed.dto.response.TemperatureDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedComment;
import com.otboo.team03.feed.repository.FeedBatchQueryRepository;
import com.otboo.team03.feed.repository.FeedClothesRepository;
import com.otboo.team03.feed.repository.FeedWeatherLookupRepository;
import com.otboo.team03.global.storage.ImageStorage;
import com.otboo.team03.user.exception.UserNotFoundException;
import com.otboo.team03.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedDtoAssemblerBatchTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private FeedClothesRepository feedClothesRepository;

  @Mock
  private FeedWeatherLookupRepository weatherRepository;

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private ObjectProvider<ImageStorage> imageStorageProvider;

  @Mock
  private ImageStorage imageStorage;

  @Mock
  private FeedBatchQueryRepository batchQueryRepository;

  @InjectMocks
  private FeedDtoAssembler assembler;

  @Test
  void 피드와_댓글이_없는_경우_일괄_조회하지_않는다() {
    // given
    List<Feed> feeds = List.of();
    List<FeedComment> comments = List.of();

    // when
    List<FeedDto> feedDtos = assembler.toDtos(feeds, null);
    List<CommentDto> commentDtos = assembler.toCommentDtos(comments);

    // then
    assertTrue(feedDtos.isEmpty());
    assertTrue(commentDtos.isEmpty());
    verifyNoInteractions(batchQueryRepository, imageStorageProvider);
  }

  @Test
  void 여러_피드의_작성자_날씨_의상_속성_집계_이미지를_일괄_변환한다() {
    // given
    UUID firstFeedId = UUID.randomUUID();
    UUID secondFeedId = UUID.randomUUID();
    UUID firstAuthorId = UUID.randomUUID();
    UUID secondAuthorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID viewerId = UUID.randomUUID();
    UUID shirtId = UUID.randomUUID();
    UUID pantsId = UUID.randomUUID();
    UUID definitionId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-11-10T01:00:00Z");

    Feed firstFeed = mockFeed(
        firstFeedId, firstAuthorId, weatherId, "첫 번째 피드", createdAt
    );
    Feed secondFeed = mockFeed(
        secondFeedId, secondAuthorId, weatherId, "두 번째 피드", createdAt
    );

    AuthorDto firstAuthor = new AuthorDto(
        firstAuthorId, "첫 번째 작성자", "/images/author1.png"
    );
    AuthorDto secondAuthor = new AuthorDto(
        secondAuthorId, "두 번째 작성자", null
    );
    WeatherSummaryDto weather = weather(weatherId);
    ClothesAttributeWithDefDto color = new ClothesAttributeWithDefDto(
        definitionId, "색상", List.of("흰색", "검정"), "흰색"
    );

    when(batchQueryRepository.findAuthors(
        Set.of(firstAuthorId, secondAuthorId)
    )).thenReturn(Map.of(
        firstAuthorId, firstAuthor,
        secondAuthorId, secondAuthor
    ));
    when(batchQueryRepository.findWeathers(Set.of(weatherId)))
        .thenReturn(Map.of(weatherId, weather));
    when(batchQueryRepository.findCounts(
        Set.of(firstFeedId, secondFeedId)
    )).thenReturn(Map.of(
        firstFeedId, new FeedBatchQueryRepository.FeedCounts(2L, 3)
    ));
    when(batchQueryRepository.findLikedFeedIds(
        Set.of(firstFeedId, secondFeedId), viewerId
    )).thenReturn(Set.of(firstFeedId));
    when(batchQueryRepository.findClothes(
        Set.of(firstFeedId, secondFeedId)
    )).thenReturn(List.of(
        new FeedBatchQueryRepository.ClothesRow(
            firstFeedId, shirtId, "셔츠", ClothesType.TOP,
            "/images/shirt.png", null
        ),
        new FeedBatchQueryRepository.ClothesRow(
            firstFeedId, pantsId, "바지", ClothesType.BOTTOM,
            "/images/pants.png", "clothes/pants.png"
        )
    ));
    when(batchQueryRepository.findClothesAttributes(
        Set.of(shirtId, pantsId)
    )).thenReturn(Map.of(shirtId, List.of(color)));
    when(imageStorageProvider.getObject()).thenReturn(imageStorage);
    when(imageStorage.getAccessUrl("clothes/pants.png"))
        .thenReturn("https://example.com/signed-pants.png");

    // when
    List<FeedDto> result = assembler.toDtos(
        List.of(firstFeed, secondFeed), viewerId
    );

    // then
    assertEquals(2, result.size());
    FeedDto first = result.get(0);
    FeedDto second = result.get(1);

    assertEquals(firstFeedId, first.id());
    assertEquals("첫 번째 피드", first.content());
    assertEquals(createdAt, first.createdAt());
    assertEquals(firstAuthor, first.author());
    assertEquals(weather, first.weather());
    assertEquals(2L, first.likeCount());
    assertEquals(3, first.commentCount());
    assertTrue(first.likedByMe());
    assertEquals(2, first.ootds().size());
    assertEquals(shirtId, first.ootds().get(0).clothesId());
    assertEquals("/images/shirt.png", first.ootds().get(0).imageUrl());
    assertEquals(List.of(color), first.ootds().get(0).attributes());
    assertEquals(pantsId, first.ootds().get(1).clothesId());
    assertEquals("https://example.com/signed-pants.png",
        first.ootds().get(1).imageUrl());
    assertTrue(first.ootds().get(1).attributes().isEmpty());

    assertEquals(secondFeedId, second.id());
    assertEquals(secondAuthor, second.author());
    assertEquals(weather, second.weather());
    assertTrue(second.ootds().isEmpty());
    assertEquals(0L, second.likeCount());
    assertEquals(0, second.commentCount());
    assertFalse(second.likedByMe());

    verify(batchQueryRepository).findAuthors(
        Set.of(firstAuthorId, secondAuthorId)
    );
    verify(batchQueryRepository).findWeathers(Set.of(weatherId));
    verify(batchQueryRepository).findCounts(Set.of(firstFeedId, secondFeedId));
    verify(batchQueryRepository).findLikedFeedIds(
        Set.of(firstFeedId, secondFeedId), viewerId
    );
    verify(batchQueryRepository).findClothes(Set.of(firstFeedId, secondFeedId));
    verify(batchQueryRepository).findClothesAttributes(Set.of(shirtId, pantsId));
    verify(imageStorage).getAccessUrl("clothes/pants.png");
  }

  @Test
  void 비로그인_피드_목록에_의상이_없으면_빈_의상과_좋아요를_반환한다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    Feed feed = mockFeed(
        feedId, authorId, weatherId, "의상 없는 피드", Instant.now()
    );
    AuthorDto author = new AuthorDto(authorId, "작성자", null);

    when(batchQueryRepository.findAuthors(Set.of(authorId)))
        .thenReturn(Map.of(authorId, author));
    when(batchQueryRepository.findWeathers(Set.of(weatherId)))
        .thenReturn(Map.of(weatherId, weather(weatherId)));
    when(batchQueryRepository.findCounts(Set.of(feedId)))
        .thenReturn(Map.of());
    when(batchQueryRepository.findLikedFeedIds(Set.of(feedId), null))
        .thenReturn(Set.of());
    when(batchQueryRepository.findClothes(Set.of(feedId)))
        .thenReturn(List.of());
    when(batchQueryRepository.findClothesAttributes(Set.of()))
        .thenReturn(Map.of());

    // when
    FeedDto result = assembler.toDtos(List.of(feed), null).get(0);

    // then
    assertEquals(author, result.author());
    assertTrue(result.ootds().isEmpty());
    assertEquals(0L, result.likeCount());
    assertEquals(0, result.commentCount());
    assertFalse(result.likedByMe());
    verifyNoInteractions(imageStorageProvider);
  }

  @Test
  void 피드_작성자가_없으면_예외가_발생한다() {
    // given
    UUID authorId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    Feed feed = mock(Feed.class);
    when(feed.getId()).thenReturn(feedId);
    when(feed.getAuthorId()).thenReturn(authorId);
    when(feed.getWeatherId()).thenReturn(weatherId);

    // when
    UserNotFoundException exception = assertThrows(
        UserNotFoundException.class,
        () -> assembler.toDtos(List.of(feed), null)
    );

    // then
    assertNotNull(exception);
  }

  @Test
  void 피드_날씨가_없으면_예외가_발생한다() {
    // given
    UUID authorId = UUID.randomUUID();
    UUID feedId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    Feed feed = mock(Feed.class);
    when(feed.getId()).thenReturn(feedId);
    when(feed.getAuthorId()).thenReturn(authorId);
    when(feed.getWeatherId()).thenReturn(weatherId);
    when(batchQueryRepository.findAuthors(Set.of(authorId)))
        .thenReturn(Map.of(authorId, new AuthorDto(authorId, "작성자", null)));

    // when
    NullPointerException exception = assertThrows(
        NullPointerException.class,
        () -> assembler.toDtos(List.of(feed), null)
    );

    // then
    assertEquals("Weather not found", exception.getMessage());
  }

  @Test
  void 댓글_일괄변환은_작성자를_중복없이_조회하고_순서를_유지한다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorAId = UUID.randomUUID();
    UUID authorBId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-11-10T01:00:00Z");
    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    UUID thirdId = UUID.randomUUID();

    FeedComment first = mockComment(
        firstId, feedId, authorAId, "첫 댓글", createdAt
    );
    FeedComment second = mockComment(
        secondId, feedId, authorAId, "둘째 댓글", createdAt.plusSeconds(1)
    );
    FeedComment third = mockComment(
        thirdId, feedId, authorBId, "셋째 댓글", createdAt.plusSeconds(2)
    );
    AuthorDto authorA = new AuthorDto(authorAId, "작성자 A", "/a.png");
    AuthorDto authorB = new AuthorDto(authorBId, "작성자 B", "/b.png");

    when(batchQueryRepository.findAuthors(Set.of(authorAId, authorBId)))
        .thenReturn(Map.of(authorAId, authorA, authorBId, authorB));

    // when
    List<CommentDto> result = assembler.toCommentDtos(
        List.of(first, second, third)
    );

    // then
    assertEquals(3, result.size());
    assertEquals(firstId, result.get(0).id());
    assertEquals("첫 댓글", result.get(0).content());
    assertEquals(authorA, result.get(0).author());
    assertEquals(createdAt, result.get(0).createdAt());
    assertEquals(secondId, result.get(1).id());
    assertEquals(authorA, result.get(1).author());
    assertEquals(thirdId, result.get(2).id());
    assertEquals(authorB, result.get(2).author());
    assertEquals(feedId, result.get(2).feedId());
    verify(batchQueryRepository, times(1)).findAuthors(Set.of(authorAId, authorBId));
  }

  @Test
  void 댓글_작성자가_없으면_예외가_발생한다() {
    // given
    UUID authorId = UUID.randomUUID();
    FeedComment comment = mock(FeedComment.class);
    when(comment.getAuthorId()).thenReturn(authorId);

    // when
    UserNotFoundException exception = assertThrows(
        UserNotFoundException.class,
        () -> assembler.toCommentDtos(List.of(comment))
    );

    // then
    assertNotNull(exception);
    verify(batchQueryRepository).findAuthors(Set.of(authorId));
  }

  private Feed mockFeed(
      UUID feedId, UUID authorId, UUID weatherId,
      String content, Instant createdAt
  ) {
    Feed feed = mock(Feed.class);
    when(feed.getId()).thenReturn(feedId);
    when(feed.getAuthorId()).thenReturn(authorId);
    when(feed.getWeatherId()).thenReturn(weatherId);
    when(feed.getCreatedAt()).thenReturn(createdAt);
    when(feed.getUpdatedAt()).thenReturn(createdAt);
    when(feed.getContent()).thenReturn(content);
    return feed;
  }

  private FeedComment mockComment(
      UUID commentId, UUID feedId, UUID authorId,
      String content, Instant createdAt
  ) {
    FeedComment comment = mock(FeedComment.class);
    when(comment.getId()).thenReturn(commentId);
    when(comment.getFeedId()).thenReturn(feedId);
    when(comment.getAuthorId()).thenReturn(authorId);
    when(comment.getContent()).thenReturn(content);
    when(comment.getCreatedAt()).thenReturn(createdAt);
    return comment;
  }

  private WeatherSummaryDto weather(UUID weatherId) {
    return new WeatherSummaryDto(
        weatherId,
        "CLEAR",
        new PrecipitationDto("NONE", 0.0, 0.0),
        new TemperatureDto(20.0, null, null, null)
    );
  }
}
