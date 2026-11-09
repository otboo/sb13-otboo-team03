package com.otboo.team03.follow.service;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.repository.FollowRepository;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

  @Mock
  private FollowRepository followRepository;

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
}
