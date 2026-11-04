package com.otboo.team03.feed.service;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.entity.ClothesType;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.request.FeedUpdateRequest;
import com.otboo.team03.feed.dto.response.AuthorDto;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.dto.response.PrecipitationDto;
import com.otboo.team03.feed.dto.response.TemperatureDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedClothes;
import com.otboo.team03.feed.mapper.FeedDtoAssembler;
import com.otboo.team03.feed.repository.FeedClothesRepository;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.feed.repository.FeedWeatherLookupRepository;
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
}
