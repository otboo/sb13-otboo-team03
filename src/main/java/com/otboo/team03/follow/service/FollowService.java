package com.otboo.team03.follow.service;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.follow.mapper.FollowDtoAssembler;
import com.otboo.team03.follow.repository.FollowRepository;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowService {

  private final FollowRepository followRepository;
  private final FollowDtoAssembler followDtoAssembler;
  private final UserRepository userRepository;

  @Transactional
  public FollowDto create(
      UUID requesterId,
      FollowCreateRequest request
  ) {
    if (request == null
        || request.followerId() == null
        || request.followeeId() == null) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (!requesterId.equals(request.followerId())) {
      throw new OtbooException(ErrorCode.ACCESS_DENIED);
    }

    if (request.followerId().equals(request.followeeId())) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (followRepository.existsByFollowerIdAndFolloweeId(
        request.followerId(),
        request.followeeId()
    )) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (!userRepository.existsById(request.followeeId())) {
      throw new OtbooException(ErrorCode.NOT_FOUND_USER);
    }

    Follow follow = new Follow(
        request.followerId(),
        request.followeeId()
    );

    Follow savedFollow = followRepository.save(follow);

    return followDtoAssembler.toDto(savedFollow);
  }

  @Transactional
  public void delete(UUID followId, UUID requesterId) {
    Follow follow = followRepository.findById(followId)
        .orElseThrow(
            () -> new OtbooException(ErrorCode.NOT_FOUND_RESOURCE)
        );

    if (!follow.getFollowerId().equals(requesterId)) {
      throw new OtbooException(ErrorCode.ACCESS_DENIED);
    }

    followRepository.delete(follow);
  }
}
