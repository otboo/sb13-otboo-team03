package com.otboo.team03.follow.service;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.follow.mapper.FollowDtoAssembler;
import com.otboo.team03.follow.repository.FollowRepository;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowService {

  private final FollowRepository followRepository;
  private final FollowDtoAssembler followDtoAssembler;

  @Transactional
  public FollowDto create(
      UUID requesterId,
      FollowCreateRequest request
  ) {
    if (!requesterId.equals(request.followerId())) {
      throw new OtbooException(ErrorCode.ACCESS_DENIED);
    }

    if (followRepository.existsByFollowerIdAndFolloweeId(
        request.followerId(),
        request.followeeId()
    )) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    Follow follow = new Follow(
        request.followerId(),
        request.followeeId()
    );

    Follow savedFollow = followRepository.save(follow);

    return followDtoAssembler.toDto(savedFollow);
  }
}
