package com.otboo.team03.follow.service;

import com.otboo.team03.follow.dto.request.FollowCreateRequest;
import com.otboo.team03.follow.dto.response.FollowDto;
import com.otboo.team03.follow.entity.Follow;
import com.otboo.team03.follow.mapper.FollowDtoAssembler;
import com.otboo.team03.follow.repository.FollowRepository;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.repository.UserRepository;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowService {

  private static final String FOLLOW_UNIQUE_CONSTRAINT =
      "uq_follows_follower_followee";

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

    Follow savedFollow;

    try {
      savedFollow = followRepository.save(follow);
      followRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      if (isDuplicateFollowConstraint(ex)) {
        throw new OtbooException(
            ErrorCode.INVALID_INPUT_VALUE,
            ex
        );
      }

      throw ex;
    }

    return followDtoAssembler.toDto(savedFollow);
  }

  private boolean isDuplicateFollowConstraint(
      DataIntegrityViolationException exception
  ) {
    Throwable cause = exception;

    while (cause != null) {
      if (cause instanceof ConstraintViolationException violation
          && FOLLOW_UNIQUE_CONSTRAINT.equals(
          violation.getConstraintName()
      )) {
        return true;
      }

      cause = cause.getCause();
    }

    return false;
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

  @Transactional(readOnly = true)
  public CursorResponse<FollowDto> getFollowers(
      UUID followeeId,
      int limit,
      String cursor,
      UUID idAfter,
      String nameLike
  ) {
    if (followeeId == null
        || limit <= 0
        || limit == Integer.MAX_VALUE) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if ((cursor == null) != (idAfter == null)) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    Instant cursorTime = null;

    if (cursor != null) {
      try {
        cursorTime = Instant.parse(cursor);
      } catch (DateTimeParseException ex) {
        throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
      }
    }

    String normalizedNameLike =
        nameLike == null || nameLike.isBlank()
            ? null
            : nameLike.trim();

    List<Follow> fetched;

    if (cursorTime == null) {
      if (normalizedNameLike == null) {
        fetched =
            followRepository.findByFolloweeIdOrderByCreatedAtDescIdDesc(
                followeeId,
                PageRequest.of(0, limit + 1)
            );
      } else {
        fetched = followRepository.findFollowersByNameLike(
            followeeId,
            normalizedNameLike,
            PageRequest.of(0, limit + 1)
        );
      }
    } else {
      if (normalizedNameLike == null) {
        fetched = followRepository.findNextPage(
            followeeId,
            cursorTime,
            idAfter,
            PageRequest.of(0, limit + 1)
        );
      } else {
        fetched = followRepository.findNextPageByNameLike(
            followeeId,
            normalizedNameLike,
            cursorTime,
            idAfter,
            PageRequest.of(0, limit + 1)
        );
      }
    }

    boolean hasNext = fetched.size() > limit;

    List<Follow> page = hasNext
        ? fetched.subList(0, limit)
        : fetched;

    List<FollowDto> data = page.isEmpty()
        ? List.of()
        : followDtoAssembler.toDtos(page);

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Follow lastFollow = page.get(page.size() - 1);

      nextCursor = lastFollow.getCreatedAt().toString();
      nextIdAfter = lastFollow.getId();
    }

    long totalCount = normalizedNameLike == null
        ? followRepository.countByFolloweeId(followeeId)
        : followRepository.countFollowersByNameLike(
            followeeId,
            normalizedNameLike
        );

    return new CursorResponse<>(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        "DESCENDING"
    );
  }

  @Transactional(readOnly = true)
  public CursorResponse<FollowDto> getFollowings(
      UUID followerId,
      int limit,
      String cursor,
      UUID idAfter,
      String nameLike
  ) {
    if (followerId == null
        || limit <= 0
        || limit == Integer.MAX_VALUE) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if ((cursor == null) != (idAfter == null)) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    Instant cursorTime = null;

    if (cursor != null) {
      try {
        cursorTime = Instant.parse(cursor);
      } catch (DateTimeParseException ex) {
        throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
      }
    }

    String normalizedNameLike =
        nameLike == null || nameLike.isBlank()
            ? null
            : nameLike.trim();

    List<Follow> fetched;

    if (cursorTime == null) {
      if (normalizedNameLike == null) {
        fetched =
            followRepository.findByFollowerIdOrderByCreatedAtDescIdDesc(
                followerId,
                PageRequest.of(0, limit + 1)
            );
      } else {
        fetched = followRepository.findFollowingsByNameLike(
            followerId,
            normalizedNameLike,
            PageRequest.of(0, limit + 1)
        );
      }
    } else {
      if (normalizedNameLike == null) {
        fetched = followRepository.findNextFollowingsPage(
            followerId,
            cursorTime,
            idAfter,
            PageRequest.of(0, limit + 1)
        );
      } else {
        fetched = followRepository.findNextFollowingsPageByNameLike(
            followerId,
            normalizedNameLike,
            cursorTime,
            idAfter,
            PageRequest.of(0, limit + 1)
        );
      }
    }

    boolean hasNext = fetched.size() > limit;

    List<Follow> page = hasNext
        ? fetched.subList(0, limit)
        : fetched;

    List<FollowDto> data = page.isEmpty()
        ? List.of()
        : followDtoAssembler.toDtos(page);

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Follow lastFollow = page.get(page.size() - 1);

      nextCursor = lastFollow.getCreatedAt().toString();
      nextIdAfter = lastFollow.getId();
    }

    long totalCount = normalizedNameLike == null
        ? followRepository.countByFollowerId(followerId)
        : followRepository.countFollowingsByNameLike(
            followerId,
            normalizedNameLike
        );

    return new CursorResponse<>(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        "DESCENDING"
    );
  }
}
