package com.otboo.team03.feed.service;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.request.FeedUpdateRequest;
import com.otboo.team03.feed.dto.request.CommentCreateRequest;
import com.otboo.team03.feed.dto.response.AuthorDto;
import com.otboo.team03.feed.dto.response.CommentDto;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.dto.response.PrecipitationDto;
import com.otboo.team03.feed.dto.response.TemperatureDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedClothes;
import com.otboo.team03.feed.entity.FeedComment;
import com.otboo.team03.feed.entity.FeedLike;
import com.otboo.team03.feed.mapper.FeedDtoAssembler;
import com.otboo.team03.feed.repository.FeedClothesRepository;
import com.otboo.team03.feed.repository.FeedCommentRepository;
import com.otboo.team03.feed.repository.FeedLikeRepository;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.feed.repository.FeedWeatherLookupRepository;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

  @Mock
  private FeedRepository feedRepository;

  @Mock
  private FeedClothesRepository feedClothesRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ClothesRepository clothesRepository;

  @Mock
  private FeedWeatherLookupRepository weatherRepository;

  @Mock
  private FeedDtoAssembler feedDtoAssembler;

  @InjectMocks
  private FeedService feedService;

  @Mock
  private FeedLikeRepository feedLikeRepository;

  @Mock
  private FeedCommentRepository feedCommentRepository;

  @Test
  void 피드_생성시_의상을_연결하고_FeedDto를_반환한다() {
    // given
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID firstClothesId = UUID.randomUUID();
    UUID secondClothesId = UUID.randomUUID();

    User author = mock(User.class);
    when(author.getId()).thenReturn(authorId);

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(firstClothesId, secondClothesId),
        "오늘의 코디"
    );

    Clothes firstClothes = new Clothes(
        "흰색 티셔츠",
        ClothesType.TOP,
        null,
        author
    );

    Clothes secondClothes = new Clothes(
        "청바지",
        ClothesType.BOTTOM,
        null,
        author
    );

    FeedDto expectedResponse = new FeedDto(
        UUID.randomUUID(),
        Instant.now(),
        Instant.now(),
        new AuthorDto(authorId, "테스트 사용자", null),
        new WeatherSummaryDto(
            weatherId,
            "CLEAR",
            new PrecipitationDto("NONE", 0.0, 0.0),
            new TemperatureDto(20.0, null, null, null)
        ),
        List.of(),
        "오늘의 코디",
        0L,
        0,
        false
    );

    when(userRepository.existsById(authorId))
        .thenReturn(true);
    when(weatherRepository.existsById(weatherId))
        .thenReturn(true);
    when(clothesRepository.findAllById(request.clothesIds()))
        .thenReturn(List.of(firstClothes, secondClothes));
    when(feedRepository.save(any(Feed.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(feedDtoAssembler.toDto(any(Feed.class), isNull()))
        .thenReturn(expectedResponse);

    // when
    FeedDto result = feedService.create(request);

    // then
    ArgumentCaptor<Feed> feedCaptor =
        ArgumentCaptor.forClass(Feed.class);

    ArgumentCaptor<FeedClothes> clothesCaptor =
        ArgumentCaptor.forClass(FeedClothes.class);

    verify(feedRepository).save(feedCaptor.capture());
    verify(feedClothesRepository, times(2))
        .save(clothesCaptor.capture());

    Feed savedFeed = feedCaptor.getValue();
    List<FeedClothes> links = clothesCaptor.getAllValues();

    verify(feedDtoAssembler).toDto(same(savedFeed), isNull());

    assertAll(
        () -> assertEquals(expectedResponse, result),
        () -> assertEquals(authorId, savedFeed.getAuthorId()),
        () -> assertEquals(weatherId, savedFeed.getWeatherId()),
        () -> assertEquals("오늘의 코디", savedFeed.getContent()),
        () -> assertEquals(2, links.size()),
        () -> assertTrue(links.stream().allMatch(
            link -> link.getFeed() == savedFeed
        )),
        () -> assertTrue(links.stream().anyMatch(
            link -> link.getClothes() == firstClothes
        )),
        () -> assertTrue(links.stream().anyMatch(
            link -> link.getClothes() == secondClothes
        ))
    );
  }

  @Test
  void 작성자가_존재하지_않으면_피드를_생성할_수_없다() {
    // given
    UUID authorId = UUID.randomUUID();

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        UUID.randomUUID(),
        List.of(),
        "오늘의 코디"
    );

    when(userRepository.existsById(authorId))
        .thenReturn(false);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.create(request)
    );

    // then
    assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
    verify(feedRepository, never()).save(any(Feed.class));
    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 날씨가_존재하지_않으면_피드를_생성할_수_없다() {
    // given
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(),
        "오늘의 코디"
    );

    when(userRepository.existsById(authorId))
        .thenReturn(true);
    when(weatherRepository.existsById(weatherId))
        .thenReturn(false);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.create(request)
    );

    // then
    assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
    verify(feedRepository, never()).save(any(Feed.class));
    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 존재하지_않는_의상이_포함되면_피드를_생성할_수_없다() {
    // given
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(clothesId),
        "오늘의 코디"
    );

    when(userRepository.existsById(authorId))
        .thenReturn(true);
    when(weatherRepository.existsById(weatherId))
        .thenReturn(true);
    when(clothesRepository.findAllById(request.clothesIds()))
        .thenReturn(List.of());

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.create(request)
    );

    // then
    assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
    verify(feedRepository, never()).save(any(Feed.class));
    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 중복된_의상_ID가_있으면_피드를_생성할_수_없다() {
    // given
    UUID clothesId = UUID.randomUUID();

    FeedCreateRequest request = new FeedCreateRequest(
        UUID.randomUUID(),
        UUID.randomUUID(),
        List.of(clothesId, clothesId),
        "오늘의 코디"
    );

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.create(request)
    );

    // then
    assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
    verify(feedRepository, never()).save(any(Feed.class));
    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 다른_사용자의_의상으로_피드를_생성할_수_없다() {
    // given
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();
    UUID clothesId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();

    User otherUser = mock(User.class);
    when(otherUser.getId()).thenReturn(otherUserId);

    FeedCreateRequest request = new FeedCreateRequest(
        authorId,
        weatherId,
        List.of(clothesId),
        "오늘의 코디"
    );

    Clothes otherUserClothes = new Clothes(
        "다른 사용자의 티셔츠",
        ClothesType.TOP,
        null,
        otherUser
    );

    when(userRepository.existsById(authorId))
        .thenReturn(true);
    when(weatherRepository.existsById(weatherId))
        .thenReturn(true);
    when(clothesRepository.findAllById(request.clothesIds()))
        .thenReturn(List.of(otherUserClothes));

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.create(request)
    );

    // then
    assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode());
    verify(feedRepository, never()).save(any(Feed.class));
    verify(feedClothesRepository, never()).save(any(FeedClothes.class));
    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 작성자가_피드를_수정하면_변경된_내용과_응답을_반환한다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID weatherId = UUID.randomUUID();

    Feed feed = new Feed(
        authorId,
        weatherId,
        "수정 전 내용"
    );

    FeedUpdateRequest request =
        new FeedUpdateRequest("수정 후 내용");

    FeedDto expectedResponse = mock(FeedDto.class);

    when(feedRepository.findById(feedId))
        .thenReturn(Optional.of(feed));

    when(feedDtoAssembler.toDto(feed, authorId))
        .thenReturn(expectedResponse);

    // when
    FeedDto result = feedService.update(
        feedId,
        authorId,
        request
    );

    // then
    assertAll(
        () -> assertEquals("수정 후 내용", feed.getContent()),
        () -> assertSame(expectedResponse, result)
    );

    verify(feedRepository).findById(feedId);
    verify(feedDtoAssembler).toDto(feed, authorId);
  }

  @Test
  void 존재하지_않는_피드는_수정할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    FeedUpdateRequest request = new FeedUpdateRequest("수정 내용");

    when(feedRepository.findById(feedId))
        .thenReturn(Optional.empty());

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.update(feedId, requesterId, request)
    );

    // then
    assertEquals(ErrorCode.NOT_FOUND_RESOURCE, exception.getErrorCode());
    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 작성자가_아니면_피드를_수정할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    Feed feed = new Feed(
        authorId,
        UUID.randomUUID(),
        "수정 전 내용"
    );

    FeedUpdateRequest request = new FeedUpdateRequest("수정 후 내용");

    when(feedRepository.findById(feedId))
        .thenReturn(Optional.of(feed));

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.update(feedId, requesterId, request)
    );

    // then
    assertAll(
        () -> assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode()),
        () -> assertEquals("수정 전 내용", feed.getContent())
    );

    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 수정_내용이_공백이면_피드를_수정할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    Feed feed = new Feed(
        authorId,
        UUID.randomUUID(),
        "수정 전 내용"
    );

    FeedUpdateRequest request = new FeedUpdateRequest("   ");

    when(feedRepository.findById(feedId))
        .thenReturn(Optional.of(feed));

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.update(feedId, authorId, request)
    );

    // then
    assertAll(
        () -> assertEquals(ErrorCode.INVALID_INPUT_VALUE, exception.getErrorCode()),
        () -> assertEquals("수정 전 내용", feed.getContent())
    );

    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 작성자가_피드를_삭제하면_저장소에서_삭제한다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    Feed feed = new Feed(
        authorId,
        UUID.randomUUID(),
        "삭제할 피드"
    );

    when(feedRepository.findById(feedId))
        .thenReturn(Optional.of(feed));

    // when
    feedService.delete(feedId, authorId);

    // then
    verify(feedRepository).findById(feedId);
    verify(feedRepository).delete(feed);
  }

  @Test
  void 존재하지_않는_피드는_삭제할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    when(feedRepository.findById(feedId))
        .thenReturn(Optional.empty());

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.delete(feedId, requesterId)
    );

    // then
    assertEquals(ErrorCode.NOT_FOUND_RESOURCE, exception.getErrorCode());

    verify(feedRepository).findById(feedId);
    verifyNoMoreInteractions(feedRepository);
  }

  @Test
  void 작성자가_아니면_피드를_삭제할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    Feed feed = new Feed(
        authorId,
        UUID.randomUUID(),
        "삭제하면 안 되는 피드"
    );

    when(feedRepository.findById(feedId))
        .thenReturn(Optional.of(feed));

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.delete(feedId, requesterId)
    );

    // then
    assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());

    verify(feedRepository).findById(feedId);
    verifyNoMoreInteractions(feedRepository);
  }

  @Test
  void 수정_요청이나_내용이_null이면_피드를_수정할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    Feed feed = new Feed(
        authorId,
        UUID.randomUUID(),
        "수정 전 내용"
    );

    when(feedRepository.findById(feedId))
        .thenReturn(Optional.of(feed));

    // when
    OtbooException nullRequestException = assertThrows(
        OtbooException.class,
        () -> feedService.update(feedId, authorId, null)
    );

    OtbooException nullContentException = assertThrows(
        OtbooException.class,
        () -> feedService.update(
            feedId,
            authorId,
            new FeedUpdateRequest(null)
        )
    );

    // then
    assertAll(
        () -> assertEquals(
            ErrorCode.INVALID_INPUT_VALUE,
            nullRequestException.getErrorCode()
        ),
        () -> assertEquals(
            ErrorCode.INVALID_INPUT_VALUE,
            nullContentException.getErrorCode()
        ),
        () -> assertEquals("수정 전 내용", feed.getContent())
    );

    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 사용자가_피드에_좋아요를_누르면_좋아요_관계가_저장된다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    // when
    feedService.like(feedId, userId);

    // then
    verify(feedRepository).existsById(feedId);

    verify(feedLikeRepository).save(argThat(
        like -> feedId.equals(like.getFeedId())
            && userId.equals(like.getUserId())
    ));
  }

  @Test
  void 사용자가_피드의_좋아요를_취소하면_좋아요_관계가_삭제된다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    // when
    feedService.unlike(feedId, userId);

    // then
    verify(feedRepository).existsById(feedId);
    verify(feedLikeRepository)
        .deleteByFeedIdAndUserId(feedId, userId);
  }

  @Test
  void 존재하지_않는_피드에는_좋아요를_등록할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(feedRepository.existsById(feedId))
        .thenReturn(false);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.like(feedId, userId)
    );

    // then
    assertEquals(
        ErrorCode.NOT_FOUND_RESOURCE,
        exception.getErrorCode()
    );

    verifyNoInteractions(feedLikeRepository);
  }

  @Test
  void 존재하지_않는_피드의_좋아요는_취소할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(feedRepository.existsById(feedId))
        .thenReturn(false);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.unlike(feedId, userId)
    );

    // then
    assertEquals(
        ErrorCode.NOT_FOUND_RESOURCE,
        exception.getErrorCode()
    );

    verifyNoInteractions(feedLikeRepository);
  }

  @Test
  void 이미_좋아요한_피드에는_좋아요를_중복_저장하지_않는다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    when(feedLikeRepository.existsByFeedIdAndUserId(feedId, userId))
        .thenReturn(true);

    // when
    feedService.like(feedId, userId);

    // then
    verify(feedLikeRepository)
        .existsByFeedIdAndUserId(feedId, userId);

    verify(feedLikeRepository, never())
        .save(any(FeedLike.class));
  }

  @Test
  void 사용자가_피드에_댓글을_등록하면_댓글이_저장된다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    CommentCreateRequest request = new CommentCreateRequest(
        feedId,
        authorId,
        "좋은 코디네요!"
    );

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    // when
    feedService.createComment(feedId, authorId, request);

    // then
    verify(feedRepository).existsById(feedId);

    verify(feedCommentRepository).save(argThat(
        comment -> feedId.equals(comment.getFeedId())
            && authorId.equals(comment.getAuthorId())
            && "좋은 코디네요!".equals(comment.getContent())
    ));
  }

  @Test
  void 공백으로만_이루어진_댓글은_등록할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    CommentCreateRequest request = new CommentCreateRequest(
        feedId,
        authorId,
        "   "
    );

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.createComment(feedId, authorId, request)
    );

    // then
    assertEquals(
        ErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );

    verifyNoInteractions(feedCommentRepository);
  }

  @Test
  void 댓글_요청의_피드_ID가_경로와_다르면_등록할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID otherFeedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    CommentCreateRequest request = new CommentCreateRequest(
        otherFeedId,
        authorId,
        "좋은 코디네요!"
    );

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.createComment(feedId, authorId, request)
    );

    // then
    assertEquals(
        ErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );

    verifyNoInteractions(feedCommentRepository);
  }

  @Test
  void 댓글_작성자_ID가_인증_사용자와_다르면_등록할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID otherAuthorId = UUID.randomUUID();

    CommentCreateRequest request = new CommentCreateRequest(
        feedId,
        otherAuthorId,
        "좋은 코디네요!"
    );

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.createComment(feedId, authorId, request)
    );

    // then
    assertEquals(
        ErrorCode.ACCESS_DENIED,
        exception.getErrorCode()
    );

    verifyNoInteractions(feedCommentRepository);
  }

  @Test
  void 댓글_등록에_성공하면_CommentDto를_반환한다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    CommentCreateRequest request = new CommentCreateRequest(
        feedId,
        authorId,
        "좋은 코디네요!"
    );

    CommentDto expectedResponse = new CommentDto(
        UUID.randomUUID(),
        Instant.now(),
        feedId,
        new AuthorDto(
            authorId,
            "댓글 작성자",
            "/images/profile.png"
        ),
        "좋은 코디네요!"
    );

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    when(feedCommentRepository.save(any(FeedComment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    when(feedDtoAssembler.toCommentDto(any(FeedComment.class)))
        .thenReturn(expectedResponse);

    // when
    CommentDto result = feedService.createComment(
        feedId,
        authorId,
        request
    );

    // then
    assertEquals(expectedResponse, result);

    verify(feedCommentRepository).save(any(FeedComment.class));
    verify(feedDtoAssembler).toCommentDto(any(FeedComment.class));
  }

  @Test
  void 댓글_최신순_첫_페이지를_조회하면_커서_응답을_반환한다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    Instant firstTime = Instant.parse("2026-11-05T03:00:00Z");
    Instant secondTime = Instant.parse("2026-11-05T02:00:00Z");

    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();

    FeedComment firstComment = mock(FeedComment.class);
    FeedComment secondComment = mock(FeedComment.class);
    FeedComment thirdComment = mock(FeedComment.class);

    CommentDto firstDto = new CommentDto(
        firstId,
        firstTime,
        feedId,
        new AuthorDto(authorId, "작성자", null),
        "첫 번째 댓글"
    );

    CommentDto secondDto = new CommentDto(
        secondId,
        secondTime,
        feedId,
        new AuthorDto(authorId, "작성자", null),
        "두 번째 댓글"
    );

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    when(feedCommentRepository.findByFeedIdOrderByCreatedAtDescIdDesc(
        feedId,
        PageRequest.of(0, 3)
    )).thenReturn(List.of(
        firstComment,
        secondComment,
        thirdComment
    ));

    when(feedCommentRepository.countByFeedId(feedId))
        .thenReturn(3L);

    when(feedDtoAssembler.toCommentDto(firstComment))
        .thenReturn(firstDto);

    when(feedDtoAssembler.toCommentDto(secondComment))
        .thenReturn(secondDto);

    when(secondComment.getCreatedAt())
        .thenReturn(secondTime);

    when(secondComment.getId())
        .thenReturn(secondId);

    // when
    CursorResponse<CommentDto> result = feedService.getFeedComments(
        feedId,
        2,
        null,
        null
    );

    // then
    assertAll(
        () -> assertEquals(List.of(firstDto, secondDto), result.data()),
        () -> assertEquals(secondTime.toString(), result.nextCursor()),
        () -> assertEquals(secondId, result.nextIdAfter()),
        () -> assertTrue(result.hasNext()),
        () -> assertEquals(3L, result.totalCount()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verify(feedCommentRepository)
        .findByFeedIdOrderByCreatedAtDescIdDesc(
            feedId,
            PageRequest.of(0, 3)
        );

    verify(feedDtoAssembler, never()).toCommentDto(thirdComment);
  }

  @Test
  void 댓글_커서_기반_두_번째_페이지를_조회한다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();

    Instant cursorTime = Instant.parse("2026-11-05T03:00:00Z");
    Instant firstTime = Instant.parse("2026-11-05T02:00:00Z");
    Instant secondTime = Instant.parse("2026-11-05T01:00:00Z");

    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();

    FeedComment firstComment = mock(FeedComment.class);
    FeedComment secondComment = mock(FeedComment.class);
    FeedComment thirdComment = mock(FeedComment.class);

    CommentDto firstDto = new CommentDto(
        firstId,
        firstTime,
        feedId,
        new AuthorDto(authorId, "작성자", null),
        "세 번째 댓글"
    );

    CommentDto secondDto = new CommentDto(
        secondId,
        secondTime,
        feedId,
        new AuthorDto(authorId, "작성자", null),
        "네 번째 댓글"
    );

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    when(feedCommentRepository.findNextPage(
        feedId,
        cursorTime,
        idAfter,
        3
    )).thenReturn(List.of(
        firstComment,
        secondComment,
        thirdComment
    ));

    when(feedCommentRepository.countByFeedId(feedId))
        .thenReturn(5L);

    when(feedDtoAssembler.toCommentDto(firstComment))
        .thenReturn(firstDto);

    when(feedDtoAssembler.toCommentDto(secondComment))
        .thenReturn(secondDto);

    when(secondComment.getCreatedAt())
        .thenReturn(secondTime);

    when(secondComment.getId())
        .thenReturn(secondId);

    // when
    CursorResponse<CommentDto> result = feedService.getFeedComments(
        feedId,
        2,
        cursorTime.toString(),
        idAfter
    );

    // then
    assertAll(
        () -> assertEquals(List.of(firstDto, secondDto), result.data()),
        () -> assertEquals(secondTime.toString(), result.nextCursor()),
        () -> assertEquals(secondId, result.nextIdAfter()),
        () -> assertTrue(result.hasNext()),
        () -> assertEquals(5L, result.totalCount()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verify(feedCommentRepository).findNextPage(
        feedId,
        cursorTime,
        idAfter,
        3
    );

    verify(feedCommentRepository, never())
        .findByFeedIdOrderByCreatedAtDescIdDesc(
            any(UUID.class),
            any(PageRequest.class)
        );

    verify(feedDtoAssembler, never())
        .toCommentDto(thirdComment);
  }

  @Test
  void 댓글_마지막_페이지에서는_다음_커서를_반환하지_않는다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();

    Instant cursorTime = Instant.parse("2026-11-05T03:00:00Z");
    Instant commentTime = Instant.parse("2026-11-05T02:00:00Z");

    FeedComment comment = mock(FeedComment.class);

    CommentDto expectedDto = new CommentDto(
        UUID.randomUUID(),
        commentTime,
        feedId,
        new AuthorDto(authorId, "작성자", null),
        "마지막 댓글"
    );

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    when(feedCommentRepository.findNextPage(
        feedId, cursorTime, idAfter, 3
    )).thenReturn(List.of(comment));

    when(feedDtoAssembler.toCommentDto(comment))
        .thenReturn(expectedDto);

    when(feedCommentRepository.countByFeedId(feedId))
        .thenReturn(3L);

    // when
    CursorResponse<CommentDto> result = feedService.getFeedComments(
        feedId,
        2,
        cursorTime.toString(),
        idAfter
    );

    // then
    assertAll(
        () -> assertEquals(List.of(expectedDto), result.data()),
        () -> assertFalse(result.hasNext()),
        () -> assertNull(result.nextCursor()),
        () -> assertNull(result.nextIdAfter()),
        () -> assertEquals(3L, result.totalCount())
    );
  }

  @Test
  void 댓글이_없는_피드는_빈_목록을_반환한다() {
    // given
    UUID feedId = UUID.randomUUID();

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    when(feedCommentRepository.findByFeedIdOrderByCreatedAtDescIdDesc(
        feedId,
        PageRequest.of(0, 11)
    )).thenReturn(List.of());

    when(feedCommentRepository.countByFeedId(feedId))
        .thenReturn(0L);

    // when
    CursorResponse<CommentDto> result = feedService.getFeedComments(
        feedId,
        10,
        null,
        null
    );

    // then
    assertAll(
        () -> assertTrue(result.data().isEmpty()),
        () -> assertFalse(result.hasNext()),
        () -> assertNull(result.nextCursor()),
        () -> assertNull(result.nextIdAfter()),
        () -> assertEquals(0L, result.totalCount())
    );

    verifyNoInteractions(feedDtoAssembler);
  }

  @Test
  void 댓글_조회_limit이_유효하지_않으면_예외가_발생한다() {
    // given
    UUID feedId = UUID.randomUUID();

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    int[] invalidLimits = {
        0,
        -1,
        Integer.MAX_VALUE
    };

    // when
    for (int limit : invalidLimits) {
      OtbooException exception = assertThrows(
          OtbooException.class,
          () -> feedService.getFeedComments(
              feedId, limit, null, null
          )
      );

      // then
      assertEquals(
          ErrorCode.INVALID_INPUT_VALUE,
          exception.getErrorCode()
      );
    }

    verifyNoInteractions(feedCommentRepository);
  }

  @Test
  void 댓글_조회_커서_형식이나_조합이_잘못되면_예외가_발생한다() {
    // given
    UUID feedId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();

    when(feedRepository.existsById(feedId))
        .thenReturn(true);

    // when
    OtbooException missingIdException = assertThrows(
        OtbooException.class,
        () -> feedService.getFeedComments(
            feedId, 10, "2026-11-05T03:00:00Z", null
        )
    );

    OtbooException missingCursorException = assertThrows(
        OtbooException.class,
        () -> feedService.getFeedComments(
            feedId, 10, null, idAfter
        )
    );

    OtbooException invalidCursorException = assertThrows(
        OtbooException.class,
        () -> feedService.getFeedComments(
            feedId, 10, "invalid-cursor", idAfter
        )
    );

    // then
    assertAll(
        () -> assertEquals(
            ErrorCode.INVALID_INPUT_VALUE,
            missingIdException.getErrorCode()
        ),
        () -> assertEquals(
            ErrorCode.INVALID_INPUT_VALUE,
            missingCursorException.getErrorCode()
        ),
        () -> assertEquals(
            ErrorCode.INVALID_INPUT_VALUE,
            invalidCursorException.getErrorCode()
        )
    );

    verifyNoInteractions(feedCommentRepository);
  }

  @Test
  void 존재하지_않는_피드의_댓글은_조회할_수_없다() {
    // given
    UUID feedId = UUID.randomUUID();

    when(feedRepository.existsById(feedId))
        .thenReturn(false);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> feedService.getFeedComments(
            feedId, 10, null, null
        )
    );

    // then
    assertEquals(
        ErrorCode.NOT_FOUND_RESOURCE,
        exception.getErrorCode()
    );

    verifyNoInteractions(feedCommentRepository);
    verifyNoInteractions(feedDtoAssembler);
  }
}
