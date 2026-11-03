package com.otboo.team03.feed.service;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedClothes;
import com.otboo.team03.feed.mapper.FeedDtoAssembler;
import com.otboo.team03.feed.repository.FeedClothesRepository;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.feed.repository.FeedWeatherLookupRepository;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.repository.UserRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedService {

  private final FeedRepository feedRepository;
  private final FeedClothesRepository feedClothesRepository;
  private final UserRepository userRepository;
  private final ClothesRepository clothesRepository;
  private final FeedWeatherLookupRepository weatherRepository;
  private final FeedDtoAssembler feedDtoAssembler;

  @Transactional
  public FeedDto create(FeedCreateRequest request) {
    validateRequest(request);

    List<UUID> clothesIds = request.clothesIds() == null
        ? List.of()
        : request.clothesIds();

    if (!userRepository.existsById(request.authorId())) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (!weatherRepository.existsById(request.weatherId())) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    List<Clothes> clothes = clothesIds.isEmpty()
        ? List.of()
        : clothesRepository.findAllById(clothesIds);

    if (clothes.size() != clothesIds.size()) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (clothes.stream()
        .anyMatch(item ->
            !request.authorId().equals(item.getOwner().getId()))) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    Feed feed = new Feed(
        request.authorId(),
        request.weatherId(),
        request.content()
    );

    Feed savedFeed = feedRepository.save(feed);

    for (Clothes item : clothes) {
      feedClothesRepository.save(
          new FeedClothes(savedFeed, item)
      );
    }

    return feedDtoAssembler.toDto(savedFeed, null);
  }

  @Transactional(readOnly = true)
  public CursorResponse<FeedDto> getFeedList(
      int limit,
      String sortBy,
      String sortDirection,
      String cursor,
      UUID idAfter,
      UUID viewerId
  ) {
    if (limit <= 0
        || limit == Integer.MAX_VALUE
        || (!"createdAt".equals(sortBy)
        && !"likeCount".equals(sortBy))
        || (!"ASCENDING".equals(sortDirection)
        && !"DESCENDING".equals(sortDirection))) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if ((cursor == null) != (idAfter == null)) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    boolean ascending = "ASCENDING".equals(sortDirection);
    boolean sortByLikeCount = "likeCount".equals(sortBy);

    if (sortByLikeCount && ascending) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    List<Feed> fetched;

    if (sortByLikeCount) {
      if (cursor == null) {
        fetched = feedRepository.findFirstPageByLikeCountDesc(
            limit + 1
        );
      } else {
        long cursorCount;

        try {
          cursorCount = Long.parseLong(cursor);
        } catch (NumberFormatException ex) {
          throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (cursorCount < 0) {
          throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
        }

        fetched = feedRepository.findNextPageByLikeCountDesc(
            cursorCount,
            idAfter,
            limit + 1
        );
      }
    } else if (cursor == null) {
      if (ascending) {
        fetched = feedRepository.findAllByOrderByCreatedAtAscIdAsc(
            PageRequest.of(0, limit + 1)
        );
      } else {
        fetched = feedRepository.findAllByOrderByCreatedAtDescIdDesc(
            PageRequest.of(0, limit + 1)
        );
      }
    } else {
      Instant cursorTime;

      try {
        cursorTime = Instant.parse(cursor);
      } catch (java.time.format.DateTimeParseException ex) {
        throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
      }

      if (ascending) {
        fetched = feedRepository.findNextPageAscending(
            cursorTime,
            idAfter,
            limit + 1
        );
      } else {
        fetched = feedRepository.findNextPage(
            cursorTime,
            idAfter,
            limit + 1
        );
      }
    }

    boolean hasNext = fetched.size() > limit;

    List<Feed> page = hasNext
        ? fetched.subList(0, limit)
        : fetched;

    List<FeedDto> data = page.stream()
        .map(feed -> feedDtoAssembler.toDto(feed, viewerId))
        .toList();

    Feed lastFeed = page.isEmpty()
        ? null
        : page.get(page.size() - 1);

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext && lastFeed != null) {
      nextIdAfter = lastFeed.getId();

      if (sortByLikeCount) {
        nextCursor = Long.toString(
            data.get(data.size() - 1).likeCount()
        );
      } else {
        nextCursor = lastFeed.getCreatedAt().toString();
      }
    }

    return new CursorResponse<>(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        feedRepository.count(),
        sortBy,
        sortDirection
    );
  }

  private void validateRequest(FeedCreateRequest request) {
    if (request == null
        || request.authorId() == null
        || request.weatherId() == null
        || request.content() == null
        || request.content().isBlank()) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    List<UUID> clothesIds = request.clothesIds();

    if (clothesIds != null
        && (clothesIds.stream().anyMatch(Objects::isNull)
        || new HashSet<>(clothesIds).size() != clothesIds.size())) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }
  }
}
