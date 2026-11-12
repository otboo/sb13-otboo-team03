package com.otboo.team03.follow.service;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.dto.response.UserSummary;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.follow.mapper.FollowDtoAssembler;
import com.otboo.team03.follow.repository.FollowRepository;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.repository.UserRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

  @Mock
  private FollowRepository followRepository;

  @Mock
  private FollowDtoAssembler followDtoAssembler;

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private FollowService followService;

  @Test
  void 로그인_사용자와_팔로워_ID가_다르면_팔로우를_생성할_수_없다() {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.create(requesterId, request)
    );

    // then
    assertEquals(ErrorCode.ACCESS_DENIED, exception.getErrorCode());
    verifyNoInteractions(followRepository);
  }

  @Test
  void 정상적인_팔로우_요청이면_팔로우_관계를_저장한다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    when(userRepository.existsById(followeeId))
        .thenReturn(true);

    // when
    followService.create(followerId, request);

    // then
    ArgumentCaptor<Follow> followCaptor =
        ArgumentCaptor.forClass(Follow.class);

    verify(followRepository).save(followCaptor.capture());

    Follow savedFollow = followCaptor.getValue();

    assertAll(
        () -> assertEquals(followerId, savedFollow.getFollowerId()),
        () -> assertEquals(followeeId, savedFollow.getFolloweeId())
    );
  }

  @Test
  void 팔로우_생성시_FollowDto를_반환한다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    Follow savedFollow = mock(Follow.class);

    FollowDto expectedResponse = new FollowDto(
        followId,
        new UserSummary(
            followeeId,
            "팔로우 대상",
            "followee.png"
        ),
        new UserSummary(
            followerId,
            "팔로워",
            "follower.png"
        )
    );

    when(userRepository.existsById(followeeId))
        .thenReturn(true);

    when(followRepository.save(any(Follow.class)))
        .thenReturn(savedFollow);

    when(followDtoAssembler.toDto(savedFollow))
        .thenReturn(expectedResponse);

    // when
    FollowDto result = followService.create(
        followerId,
        request
    );

    // then
    assertAll(
        () -> assertEquals(expectedResponse, result),
        () -> assertEquals(followId, result.id()),
        () -> assertEquals(followeeId, result.followee().userId()),
        () -> assertEquals(followerId, result.follower().userId()),
        () -> assertEquals("팔로우 대상", result.followee().name()),
        () -> assertEquals("팔로워", result.follower().name()),
        () -> assertEquals("followee.png", result.followee().profileImageUrl()),
        () -> assertEquals("follower.png", result.follower().profileImageUrl())
    );

    verify(followDtoAssembler).toDto(savedFollow);
  }

  @Test
  void 이미_팔로우한_사용자를_중복_팔로우할_수_없다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    when(followRepository.existsByFollowerIdAndFolloweeId(
        followerId,
        followeeId
    )).thenReturn(true);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.create(followerId, request)
    );

    // then
    assertEquals(
        ErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );

    verify(followRepository, never()).save(any(Follow.class));
    verifyNoInteractions(followDtoAssembler);
  }

  @Test
  void 자기_자신을_팔로우할_수_없다() {
    // given
    UUID userId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        userId,
        userId
    );

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.create(userId, request)
    );

    // then
    assertEquals(
        ErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );

    verify(followRepository, never()).save(any(Follow.class));
    verifyNoInteractions(followDtoAssembler);
  }

  @Test
  void 존재하지_않는_사용자를_팔로우할_수_없다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    when(userRepository.existsById(followeeId))
        .thenReturn(false);

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.create(followerId, request)
    );

    // then
    assertEquals(
        ErrorCode.NOT_FOUND_USER,
        exception.getErrorCode()
    );

    verify(userRepository).existsById(followeeId);
    verify(followRepository, never()).save(any(Follow.class));
    verifyNoInteractions(followDtoAssembler);
  }

  @Test
  void 팔로우_생성_요청이_null이면_예외가_발생한다() {
    // given
    UUID requesterId = UUID.randomUUID();
    FollowCreateRequest request = null;

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.create(requesterId, request)
    );

    // then
    assertEquals(
        ErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );

    verifyNoInteractions(
        followRepository,
        followDtoAssembler,
        userRepository
    );
  }

  @Test
  void 팔로워_ID가_null이면_팔로우를_생성할_수_없다() {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        null,
        followeeId
    );

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.create(requesterId, request)
    );

    // then
    assertEquals(
        ErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );

    verifyNoInteractions(
        followRepository,
        followDtoAssembler,
        userRepository
    );
  }

  @Test
  void 팔로우_대상_ID가_null이면_팔로우를_생성할_수_없다() {
    // given
    UUID followerId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        null
    );

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.create(followerId, request)
    );

    // then
    assertEquals(
        ErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );

    verifyNoInteractions(
        followRepository,
        followDtoAssembler,
        userRepository
    );
  }

  @Test
  void 본인의_팔로우를_취소하면_팔로우_관계가_삭제된다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    Follow follow = new Follow(followerId, followeeId);

    when(followRepository.findById(followId))
        .thenReturn(Optional.of(follow));

    // when
    followService.delete(followId, followerId);

    // then
    verify(followRepository).findById(followId);
    verify(followRepository).delete(follow);
    verifyNoInteractions(followDtoAssembler, userRepository);
  }

  @Test
  void 존재하지_않는_팔로우는_취소할_수_없다() {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    when(followRepository.findById(followId))
        .thenReturn(Optional.empty());

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.delete(followId, requesterId)
    );

    // then
    assertEquals(
        ErrorCode.NOT_FOUND_RESOURCE,
        exception.getErrorCode()
    );

    verify(followRepository, never()).delete(any(Follow.class));
    verifyNoInteractions(followDtoAssembler, userRepository);
  }

  @Test
  void 다른_사용자의_팔로우를_취소할_수_없다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    Follow follow = new Follow(followerId, followeeId);

    when(followRepository.findById(followId))
        .thenReturn(Optional.of(follow));

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.delete(followId, requesterId)
    );

    // then
    assertEquals(
        ErrorCode.ACCESS_DENIED,
        exception.getErrorCode()
    );

    verify(followRepository, never()).delete(any(Follow.class));
    verifyNoInteractions(followDtoAssembler, userRepository);
  }

  @Test
  void 동시_중복_팔로우의_UNIQUE_충돌은_400_예외로_변환한다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    when(userRepository.existsById(followeeId))
        .thenReturn(true);

    DataIntegrityViolationException dbException =
        new DataIntegrityViolationException(
            "Duplicate follow",
            new ConstraintViolationException(
                "Unique constraint violation",
                new SQLException("Duplicate key", "23505"),
                "uq_follows_follower_followee"
            )
        );

    doThrow(dbException)
        .when(followRepository)
        .flush();

    // when
    OtbooException exception = assertThrows(
        OtbooException.class,
        () -> followService.create(followerId, request)
    );

    // then
    assertEquals(
        ErrorCode.INVALID_INPUT_VALUE,
        exception.getErrorCode()
    );

    verify(followRepository).save(any(Follow.class));
    verify(followRepository).flush();
    verifyNoInteractions(followDtoAssembler);
  }

  @Test
  void 다른_DB_제약조건_위반은_중복_팔로우_오류로_변환하지_않는다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreateRequest request = new FollowCreateRequest(
        followerId,
        followeeId
    );

    when(userRepository.existsById(followeeId))
        .thenReturn(true);

    DataIntegrityViolationException dbException =
        new DataIntegrityViolationException(
            "Foreign key violation",
            new ConstraintViolationException(
                "Foreign key constraint violation",
                new SQLException("Foreign key violation", "23503"),
                "fk_users_to_follows_followee"
            )
        );

    doThrow(dbException)
        .when(followRepository)
        .flush();

    // when
    DataIntegrityViolationException exception = assertThrows(
        DataIntegrityViolationException.class,
        () -> followService.create(followerId, request)
    );

    // then
    assertSame(dbException, exception);

    verify(followRepository).save(any(Follow.class));
    verify(followRepository).flush();
    verifyNoInteractions(followDtoAssembler);
  }

  @Test
  void 팔로워_첫_페이지_조회시_다음_커서와_전체_건수를_반환한다() {
    // given
    UUID followeeId = UUID.randomUUID();
    UUID firstFollowId = UUID.randomUUID();
    UUID secondFollowId = UUID.randomUUID();

    Instant createdAt = Instant.parse("2026-11-09T01:00:00Z");

    Follow first = mock(Follow.class);
    Follow second = mock(Follow.class);
    Follow extra = mock(Follow.class);

    FollowDto firstDto = new FollowDto(
        firstFollowId,
        new UserSummary(followeeId, "팔로우 대상", null),
        new UserSummary(UUID.randomUUID(), "첫 번째 팔로워", null)
    );

    FollowDto secondDto = new FollowDto(
        secondFollowId,
        new UserSummary(followeeId, "팔로우 대상", null),
        new UserSummary(UUID.randomUUID(), "두 번째 팔로워", null)
    );

    when(followRepository.findByFolloweeIdOrderByCreatedAtDescIdDesc(
        followeeId,
        PageRequest.of(0, 3)
    )).thenReturn(List.of(first, second, extra));

    when(followRepository.countByFolloweeId(followeeId))
        .thenReturn(5L);

    when(followDtoAssembler.toDtos(List.of(first, second)))
        .thenReturn(List.of(firstDto, secondDto));

    when(second.getId()).thenReturn(secondFollowId);
    when(second.getCreatedAt()).thenReturn(createdAt);

    // when
    CursorResponse<FollowDto> result = followService.getFollowers(
        followeeId, 2, null, null, null
    );

    // then
    assertAll(
        () -> assertEquals(List.of(firstDto, secondDto), result.data()),
        () -> assertTrue(result.hasNext()),
        () -> assertEquals(createdAt.toString(), result.nextCursor()),
        () -> assertEquals(secondFollowId, result.nextIdAfter()),
        () -> assertEquals(5L, result.totalCount()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verify(followDtoAssembler).toDtos(List.of(first, second));
  }

  @Test
  void 팔로워가_없으면_빈_목록을_반환한다() {
    // given
    UUID followeeId = UUID.randomUUID();

    when(followRepository.findByFolloweeIdOrderByCreatedAtDescIdDesc(
        followeeId,
        PageRequest.of(0, 11)
    )).thenReturn(List.of());

    // when
    CursorResponse<FollowDto> result = followService.getFollowers(
        followeeId, 10, null, null, null
    );

    // then
    assertAll(
        () -> assertTrue(result.data().isEmpty()),
        () -> assertFalse(result.hasNext()),
        () -> assertNull(result.nextCursor()),
        () -> assertNull(result.nextIdAfter()),
        () -> assertEquals(0L, result.totalCount()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verifyNoInteractions(followDtoAssembler);
  }

  @Test
  void 팔로워_이름으로_검색하면_일치하는_목록과_전체_건수를_반환한다() {
    // given
    UUID followeeId = UUID.randomUUID();
    UUID lastFollowId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-11-09T01:00:00Z");

    Follow first = mock(Follow.class);
    Follow second = mock(Follow.class);
    Follow extra = mock(Follow.class);

    FollowDto firstDto = new FollowDto(
        UUID.randomUUID(),
        new UserSummary(followeeId, "팔로우 대상", null),
        new UserSummary(UUID.randomUUID(), "김민수", null)
    );

    FollowDto secondDto = new FollowDto(
        lastFollowId,
        new UserSummary(followeeId, "팔로우 대상", null),
        new UserSummary(UUID.randomUUID(), "이민지", null)
    );

    when(followRepository.findFollowersByNameLike(
        followeeId,
        "민",
        PageRequest.of(0, 3)
    )).thenReturn(List.of(first, second, extra));

    when(followRepository.countFollowersByNameLike(
        followeeId,
        "민"
    )).thenReturn(5L);

    when(followDtoAssembler.toDtos(List.of(first, second)))
        .thenReturn(List.of(firstDto, secondDto));

    when(second.getId()).thenReturn(lastFollowId);
    when(second.getCreatedAt()).thenReturn(createdAt);

    // when
    CursorResponse<FollowDto> result = followService.getFollowers(
        followeeId, 2, null, null, "민"
    );

    // then
    assertAll(
        () -> assertEquals(2, result.data().size()),
        () -> assertEquals("김민수", result.data().get(0).follower().name()),
        () -> assertEquals("이민지", result.data().get(1).follower().name()),
        () -> assertEquals(5L, result.totalCount()),
        () -> assertTrue(result.hasNext()),
        () -> assertEquals(createdAt.toString(), result.nextCursor()),
        () -> assertEquals(lastFollowId, result.nextIdAfter()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verify(followDtoAssembler).toDtos(List.of(first, second));
    verify(followRepository, never())
        .findByFolloweeIdOrderByCreatedAtDescIdDesc(
            any(UUID.class),
            any(PageRequest.class)
        );
  }

  @Test
  void 이름_검색어가_공백이면_전체_팔로워를_조회한다() {
    // given
    UUID followeeId = UUID.randomUUID();

    when(followRepository.findByFolloweeIdOrderByCreatedAtDescIdDesc(
        followeeId,
        PageRequest.of(0, 11)
    )).thenReturn(List.of());

    // when
    CursorResponse<FollowDto> result = followService.getFollowers(
        followeeId, 10, null, null, "   "
    );

    // then
    assertAll(
        () -> assertTrue(result.data().isEmpty()),
        () -> assertFalse(result.hasNext()),
        () -> assertEquals(0L, result.totalCount())
    );

    verify(followRepository).countByFolloweeId(followeeId);
    verifyNoInteractions(followDtoAssembler);
  }

  @Test
  void 팔로워_커서로_다음_페이지를_조회한다() {
    // given
    UUID followeeId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    UUID nextFollowId = UUID.randomUUID();

    String cursor = "2026-11-09T01:00:00Z";
    Instant cursorTime = Instant.parse(cursor);
    Instant nextCreatedAt =
        Instant.parse("2026-11-08T01:00:00Z");

    Follow first = mock(Follow.class);
    Follow extra = mock(Follow.class);

    FollowDto firstDto = new FollowDto(
        nextFollowId,
        new UserSummary(followeeId, "팔로우 대상", null),
        new UserSummary(UUID.randomUUID(), "팔로워", null)
    );

    when(followRepository.findNextPage(
        followeeId,
        cursorTime,
        idAfter,
        PageRequest.of(0, 2)
    )).thenReturn(List.of(first, extra));

    when(followRepository.countByFolloweeId(followeeId))
        .thenReturn(3L);

    when(followDtoAssembler.toDtos(List.of(first)))
        .thenReturn(List.of(firstDto));
    when(first.getId()).thenReturn(nextFollowId);
    when(first.getCreatedAt()).thenReturn(nextCreatedAt);

    // when
    CursorResponse<FollowDto> result = followService.getFollowers(
        followeeId, 1, cursor, idAfter, null
    );

    // then
    assertAll(
        () -> assertEquals(List.of(firstDto), result.data()),
        () -> assertTrue(result.hasNext()),
        () -> assertEquals(nextCreatedAt.toString(), result.nextCursor()),
        () -> assertEquals(nextFollowId, result.nextIdAfter()),
        () -> assertEquals(3L, result.totalCount()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verify(followRepository).findNextPage(
        followeeId,
        cursorTime,
        idAfter,
        PageRequest.of(0, 2)
    );

    verify(followDtoAssembler).toDtos(List.of(first));
  }

  @Test
  void 이름_검색_조건을_유지하며_팔로워_다음_페이지를_조회한다() {
    // given
    UUID followeeId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();

    String cursor = "2026-11-09T01:00:00Z";
    Instant cursorTime = Instant.parse(cursor);

    Follow follow = mock(Follow.class);

    FollowDto followDto = new FollowDto(
        UUID.randomUUID(),
        new UserSummary(followeeId, "팔로우 대상", null),
        new UserSummary(UUID.randomUUID(), "김민수", null)
    );

    when(followRepository.findNextPageByNameLike(
        followeeId,
        "민",
        cursorTime,
        idAfter,
        PageRequest.of(0, 3)
    )).thenReturn(List.of(follow));

    when(followRepository.countFollowersByNameLike(
        followeeId,
        "민"
    )).thenReturn(3L);

    when(followDtoAssembler.toDtos(List.of(follow)))
        .thenReturn(List.of(followDto));

    // when
    CursorResponse<FollowDto> result = followService.getFollowers(
        followeeId, 2, cursor, idAfter, "민"
    );

    // then
    assertAll(
        () -> assertEquals(1, result.data().size()),
        () -> assertEquals(
            "김민수",
            result.data().get(0).follower().name()
        ),
        () -> assertFalse(result.hasNext()),
        () -> assertNull(result.nextCursor()),
        () -> assertNull(result.nextIdAfter()),
        () -> assertEquals(3L, result.totalCount())
    );

    verify(followRepository).findNextPageByNameLike(
        followeeId,
        "민",
        cursorTime,
        idAfter,
        PageRequest.of(0, 3)
    );
  }

  @Test
  void 팔로워_조회_커서_형식이나_조합이_잘못되면_예외가_발생한다() {
    // given
    UUID followeeId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    String cursor = "2026-11-09T01:00:00Z";

    // when
    OtbooException missingIdAfter = assertThrows(
        OtbooException.class,
        () -> followService.getFollowers(
            followeeId, 10, cursor, null, null
        )
    );

    OtbooException missingCursor = assertThrows(
        OtbooException.class,
        () -> followService.getFollowers(
            followeeId, 10, null, idAfter, null
        )
    );

    OtbooException invalidCursor = assertThrows(
        OtbooException.class,
        () -> followService.getFollowers(
            followeeId, 10, "invalid-cursor", idAfter, null
        )
    );

    OtbooException blankCursor = assertThrows(
        OtbooException.class,
        () -> followService.getFollowers(
            followeeId, 10, "   ", idAfter, null
        )
    );

    // then
    assertAll(
        () -> assertEquals(
            ErrorCode.INVALID_INPUT_VALUE,
            missingIdAfter.getErrorCode()
        ),
        () -> assertEquals(
            ErrorCode.INVALID_INPUT_VALUE,
            missingCursor.getErrorCode()
        ),
        () -> assertEquals(
            ErrorCode.INVALID_INPUT_VALUE,
            invalidCursor.getErrorCode()
        ),
        () -> assertEquals(
            ErrorCode.INVALID_INPUT_VALUE,
            blankCursor.getErrorCode()
        )
    );

    verifyNoInteractions(
        followRepository,
        followDtoAssembler,
        userRepository
    );
  }

  @Test
  void 팔로잉_첫_페이지_조회시_다음_커서와_전체_건수를_반환한다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID firstFollowId = UUID.randomUUID();
    UUID secondFollowId = UUID.randomUUID();

    Instant createdAt = Instant.parse("2026-11-12T01:00:00Z");

    Follow first = mock(Follow.class);
    Follow second = mock(Follow.class);
    Follow extra = mock(Follow.class);

    FollowDto firstDto = new FollowDto(
        firstFollowId,
        new UserSummary(UUID.randomUUID(), "첫 번째 팔로잉", null),
        new UserSummary(followerId, "나", null)
    );

    FollowDto secondDto = new FollowDto(
        secondFollowId,
        new UserSummary(UUID.randomUUID(), "두 번째 팔로잉", null),
        new UserSummary(followerId, "나", null)
    );

    when(followRepository.findByFollowerIdOrderByCreatedAtDescIdDesc(
        followerId,
        PageRequest.of(0, 3)
    )).thenReturn(List.of(first, second, extra));

    when(followRepository.countByFollowerId(followerId))
        .thenReturn(5L);

    when(followDtoAssembler.toDtos(List.of(first, second)))
        .thenReturn(List.of(firstDto, secondDto));

    when(second.getId()).thenReturn(secondFollowId);
    when(second.getCreatedAt()).thenReturn(createdAt);

    // when
    CursorResponse<FollowDto> result = followService.getFollowings(
        followerId, 2, null, null, null
    );

    // then
    assertAll(
        () -> assertEquals(List.of(firstDto, secondDto), result.data()),
        () -> assertEquals("첫 번째 팔로잉",
            result.data().get(0).followee().name()),
        () -> assertEquals("두 번째 팔로잉",
            result.data().get(1).followee().name()),
        () -> assertTrue(result.hasNext()),
        () -> assertEquals(createdAt.toString(), result.nextCursor()),
        () -> assertEquals(secondFollowId, result.nextIdAfter()),
        () -> assertEquals(5L, result.totalCount()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verify(followDtoAssembler).toDtos(List.of(first, second));
  }

  @Test
  void 팔로잉_이름으로_검색하면_일치하는_목록과_전체_건수를_반환한다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID lastFollowId = UUID.randomUUID();
    Instant createdAt = Instant.parse("2026-11-12T01:00:00Z");

    Follow first = mock(Follow.class);
    Follow second = mock(Follow.class);
    Follow extra = mock(Follow.class);

    FollowDto firstDto = new FollowDto(
        UUID.randomUUID(),
        new UserSummary(UUID.randomUUID(), "김민수", null),
        new UserSummary(followerId, "나", null)
    );

    FollowDto secondDto = new FollowDto(
        lastFollowId,
        new UserSummary(UUID.randomUUID(), "이민지", null),
        new UserSummary(followerId, "나", null)
    );

    when(followRepository.findFollowingsByNameLike(
        followerId,
        "민",
        PageRequest.of(0, 3)
    )).thenReturn(List.of(first, second, extra));

    when(followRepository.countFollowingsByNameLike(
        followerId,
        "민"
    )).thenReturn(5L);

    when(followDtoAssembler.toDtos(List.of(first, second)))
        .thenReturn(List.of(firstDto, secondDto));

    when(second.getId()).thenReturn(lastFollowId);
    when(second.getCreatedAt()).thenReturn(createdAt);

    // when
    CursorResponse<FollowDto> result = followService.getFollowings(
        followerId, 2, null, null, "민"
    );

    // then
    assertAll(
        () -> assertEquals(2, result.data().size()),
        () -> assertEquals("김민수", result.data().get(0).followee().name()),
        () -> assertEquals("이민지", result.data().get(1).followee().name()),
        () -> assertEquals(5L, result.totalCount()),
        () -> assertTrue(result.hasNext()),
        () -> assertEquals(createdAt.toString(), result.nextCursor()),
        () -> assertEquals(lastFollowId, result.nextIdAfter()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verify(followDtoAssembler).toDtos(List.of(first, second));

    verify(followRepository, never())
        .findByFollowerIdOrderByCreatedAtDescIdDesc(
            any(UUID.class),
            any(PageRequest.class)
        );
  }

  @Test
  void 팔로잉_이름_검색어가_공백이면_전체_목록을_조회한다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    Follow follow = mock(Follow.class);

    FollowDto followDto = new FollowDto(
        followId,
        new UserSummary(followeeId, "팔로우 대상", null),
        new UserSummary(followerId, "나", null)
    );

    when(followRepository.findByFollowerIdOrderByCreatedAtDescIdDesc(
        followerId,
        PageRequest.of(0, 11)
    )).thenReturn(List.of(follow));

    when(followRepository.countByFollowerId(followerId))
        .thenReturn(1L);

    when(followDtoAssembler.toDtos(List.of(follow)))
        .thenReturn(List.of(followDto));

    // when
    CursorResponse<FollowDto> result = followService.getFollowings(
        followerId, 10, null, null, "   "
    );

    // then
    assertAll(
        () -> assertEquals(1, result.data().size()),
        () -> assertEquals(followDto, result.data().get(0)),
        () -> assertEquals(1L, result.totalCount()),
        () -> assertFalse(result.hasNext()),
        () -> assertNull(result.nextCursor()),
        () -> assertNull(result.nextIdAfter())
    );

    verify(followRepository)
        .findByFollowerIdOrderByCreatedAtDescIdDesc(
            followerId,
            PageRequest.of(0, 11)
        );

    verify(followRepository, never())
        .findFollowingsByNameLike(
            any(UUID.class),
            anyString(),
            any(PageRequest.class)
        );

    verify(followRepository, never())
        .countFollowingsByNameLike(
            any(UUID.class),
            anyString()
        );

    verify(followDtoAssembler).toDtos(List.of(follow));
  }

  @Test
  void 팔로잉_커서로_다음_페이지를_조회한다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    UUID nextFollowId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    String cursor = "2026-11-12T01:00:00Z";
    Instant cursorTime = Instant.parse(cursor);
    Instant nextCreatedAt =
        Instant.parse("2026-11-11T01:00:00Z");

    Follow first = mock(Follow.class);
    Follow extra = mock(Follow.class);

    FollowDto firstDto = new FollowDto(
        nextFollowId,
        new UserSummary(followeeId, "팔로잉 대상", null),
        new UserSummary(followerId, "나", null)
    );

    when(followRepository.findNextFollowingsPage(
        followerId,
        cursorTime,
        idAfter,
        PageRequest.of(0, 2)
    )).thenReturn(List.of(first, extra));

    when(followRepository.countByFollowerId(followerId))
        .thenReturn(3L);

    when(followDtoAssembler.toDtos(List.of(first)))
        .thenReturn(List.of(firstDto));

    when(first.getId()).thenReturn(nextFollowId);
    when(first.getCreatedAt()).thenReturn(nextCreatedAt);

    // when
    CursorResponse<FollowDto> result = followService.getFollowings(
        followerId, 1, cursor, idAfter, null
    );

    // then
    assertAll(
        () -> assertEquals(List.of(firstDto), result.data()),
        () -> assertEquals(
            "팔로잉 대상",
            result.data().get(0).followee().name()
        ),
        () -> assertTrue(result.hasNext()),
        () -> assertEquals(nextCreatedAt.toString(), result.nextCursor()),
        () -> assertEquals(nextFollowId, result.nextIdAfter()),
        () -> assertEquals(3L, result.totalCount()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verify(followRepository).findNextFollowingsPage(
        followerId,
        cursorTime,
        idAfter,
        PageRequest.of(0, 2)
    );

    verify(followDtoAssembler).toDtos(List.of(first));
  }

  @Test
  void 이름_검색_조건을_유지하며_팔로잉_다음_페이지를_조회한다() {
    // given
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    String cursor = "2026-11-12T01:00:00Z";
    Instant cursorTime = Instant.parse(cursor);

    Follow follow = mock(Follow.class);

    FollowDto followDto = new FollowDto(
        followId,
        new UserSummary(followeeId, "김민수", null),
        new UserSummary(followerId, "나", null)
    );

    when(followRepository.findNextFollowingsPageByNameLike(
        followerId,
        "민",
        cursorTime,
        idAfter,
        PageRequest.of(0, 3)
    )).thenReturn(List.of(follow));

    when(followRepository.countFollowingsByNameLike(
        followerId,
        "민"
    )).thenReturn(3L);

    when(followDtoAssembler.toDtos(List.of(follow)))
        .thenReturn(List.of(followDto));

    // when
    CursorResponse<FollowDto> result = followService.getFollowings(
        followerId, 2, cursor, idAfter, "  민  "
    );

    // then
    assertAll(
        () -> assertEquals(1, result.data().size()),
        () -> assertEquals(
            "김민수",
            result.data().get(0).followee().name()
        ),
        () -> assertFalse(result.hasNext()),
        () -> assertNull(result.nextCursor()),
        () -> assertNull(result.nextIdAfter()),
        () -> assertEquals(3L, result.totalCount()),
        () -> assertEquals("createdAt", result.sortBy()),
        () -> assertEquals("DESCENDING", result.sortDirection())
    );

    verify(followRepository).findNextFollowingsPageByNameLike(
        followerId,
        "민",
        cursorTime,
        idAfter,
        PageRequest.of(0, 3)
    );

    verify(followRepository).countFollowingsByNameLike(
        followerId,
        "민"
    );

    verify(followDtoAssembler).toDtos(List.of(follow));
  }
}
