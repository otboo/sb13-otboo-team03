package com.otboo.team03.feed.service;

import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.clothes.repository.ClothesRepository;
import com.otboo.team03.feed.dto.request.CommentCreateRequest;
import com.otboo.team03.feed.dto.request.FeedCreateRequest;
import com.otboo.team03.feed.dto.request.FeedUpdateRequest;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.FeedClothes;
import com.otboo.team03.feed.entity.FeedComment;
import com.otboo.team03.feed.entity.FeedLike;
import com.otboo.team03.feed.mapper.FeedDtoAssembler;
import com.otboo.team03.feed.repository.FeedClothesRepository;
import com.otboo.team03.feed.repository.FeedCommentRepository;
import com.otboo.team03.feed.repository.FeedLikeRepository;
import com.otboo.team03.feed.repository.FeedRepository;
import com.otboo.team03.feed.repository.FeedSearchQueryRepository;
import com.otboo.team03.feed.repository.FeedWeatherLookupRepository;
import com.otboo.team03.global.dto.CursorResponse;
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.repository.UserRepository;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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
  private final FeedSearchQueryRepository feedSearchQueryRepository;
  private final FeedLikeRepository feedLikeRepository;
  private final FeedCommentRepository feedCommentRepository;

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

  @Transactional
  public FeedDto update(
      UUID feedId,
      UUID requesterId,
      FeedUpdateRequest request
  ) {

    Feed feed = feedRepository.findById(feedId)
        .orElseThrow(
            () -> new OtbooException(ErrorCode.NOT_FOUND_RESOURCE)
        );


    if (!feed.getAuthorId().equals(requesterId)) {
      throw new OtbooException(ErrorCode.ACCESS_DENIED);
    }


    if (request == null
        || request.content() == null
        || request.content().isBlank()) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    feed.updateContent(request.content());

    return feedDtoAssembler.toDto(feed, requesterId);
  }

  @Transactional
  public void delete(UUID feedId, UUID requesterId) {
    Feed feed = feedRepository.findById(feedId)
        .orElseThrow(
            () -> new OtbooException(ErrorCode.NOT_FOUND_RESOURCE)
        );

    if (!feed.getAuthorId().equals(requesterId)) {
      throw new OtbooException(ErrorCode.ACCESS_DENIED);
    }

    feedRepository.deleteFeedLikesByFeedId(feedId);
    feedRepository.deleteFeedCommentsByFeedId(feedId);
    feedRepository.deleteFeedClothesByFeedId(feedId);

    feedRepository.delete(feed);
  }

  @Transactional
  public void like(UUID feedId, UUID userId) {
    if (!feedRepository.existsById(feedId)) {
      throw new OtbooException(ErrorCode.NOT_FOUND_RESOURCE);
    }

    if (feedLikeRepository.existsByFeedIdAndUserId(feedId, userId)) {
      return;
    }

    feedLikeRepository.save(new FeedLike(feedId, userId));
  }

  @Transactional
  public void unlike(UUID feedId, UUID userId) {
    if (!feedRepository.existsById(feedId)) {
      throw new OtbooException(ErrorCode.NOT_FOUND_RESOURCE);
    }

    feedLikeRepository.deleteByFeedIdAndUserId(feedId, userId);
  }

  @Transactional
  public void createComment(
      UUID feedId,
      UUID authorId,
      CommentCreateRequest request
  ) {
    if (!feedRepository.existsById(feedId)) {
      throw new OtbooException(ErrorCode.NOT_FOUND_RESOURCE);
    }

    if (request == null
        || request.feedId() == null
        || request.authorId() == null
        || request.content() == null
        || request.content().isBlank()) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (!feedId.equals(request.feedId())) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (!authorId.equals(request.authorId())) {
      throw new OtbooException(ErrorCode.ACCESS_DENIED);
    }

    FeedComment comment = new FeedComment(
        feedId,
        authorId,
        request.content()
    );

    feedCommentRepository.save(comment);
  }

  @Transactional(readOnly = true)
  public CursorResponse<FeedDto> getFeedList(
      int limit,
      String sortBy,
      String sortDirection,
      String cursor,
      UUID idAfter,
      String keywordLike,
      UUID authorIdEqual,
      String skyStatusEqual,
      String precipitationTypeEqual,
      UUID viewerId
  ) {
    FeedListQuery query = new FeedListQuery(
        limit,
        sortBy,
        sortDirection,
        cursor,
        idAfter,
        keywordLike,
        authorIdEqual,
        skyStatusEqual,
        precipitationTypeEqual,
        viewerId
    );

    validateFeedListQuery(query);

    List<Feed> feeds = findFeeds(query);

    return createCursorResponse(query, feeds);
  }

  private void validateFeedListQuery(FeedListQuery query) {
    if (query.limit() <= 0
        || query.limit() == Integer.MAX_VALUE
        || (!"createdAt".equals(query.sortBy())
        && !"likeCount".equals(query.sortBy()))
        || (!"ASCENDING".equals(query.sortDirection())
        && !"DESCENDING".equals(query.sortDirection()))) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if ((query.cursor() == null) != (query.idAfter() == null)) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (query.skyStatusEqual() != null
        && !Set.of("CLEAR", "MOSTLY_CLOUDY", "CLOUDY")
        .contains(query.skyStatusEqual())) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (query.precipitationTypeEqual() != null
        && !Set.of("NONE", "RAIN", "RAIN_SNOW", "SNOW", "SHOWER")
        .contains(query.precipitationTypeEqual())) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }
  }

  private List<Feed> findFeeds(FeedListQuery query) {
    return findFeedsByFilters(query);
  }

  private List<Feed> findFeedsByFilters(FeedListQuery query) {
    Instant createdAtCursor = null;
    Long likeCountCursor = null;

    if (query.cursor() != null) {
      if (query.isLikeCountSort()) {
        likeCountCursor = parseLikeCountCursor(query.cursor());
      } else {
        createdAtCursor = parseCreatedAtCursor(query.cursor());
      }
    }

    return feedSearchQueryRepository.findByFilters(
        query.keywordLike(),
        query.authorIdEqual(),
        query.skyStatusEqual(),
        query.precipitationTypeEqual(),
        query.sortBy(),
        query.sortDirection(),
        createdAtCursor,
        likeCountCursor,
        query.idAfter(),
        query.limit() + 1
    );
  }

  private long parseLikeCountCursor(String cursor) {
    long cursorCount;

    try {
      cursorCount = Long.parseLong(cursor);
    } catch (NumberFormatException ex) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    if (cursorCount < 0) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }

    return cursorCount;
  }

  private Instant parseCreatedAtCursor(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (java.time.format.DateTimeParseException ex) {
      throw new OtbooException(ErrorCode.INVALID_INPUT_VALUE);
    }
  }

  private CursorResponse<FeedDto> createCursorResponse(
      FeedListQuery query,
      List<Feed> fetched
  ) {
    boolean hasNext = fetched.size() > query.limit();

    List<Feed> page = hasNext
        ? fetched.subList(0, query.limit())
        : fetched;

    List<FeedDto> data = page.stream()
        .map(feed -> feedDtoAssembler.toDto(feed, query.viewerId()))
        .toList();

    String nextCursor = null;
    UUID nextIdAfter = null;

    if (hasNext) {
      Feed lastFeed = page.get(page.size() - 1);
      nextIdAfter = lastFeed.getId();

      nextCursor = query.isLikeCountSort()
          ? Long.toString(data.get(data.size() - 1).likeCount())
          : lastFeed.getCreatedAt().toString();
    }

    long totalCount = feedSearchQueryRepository.countByFilters(
        query.keywordLike(),
        query.authorIdEqual(),
        query.skyStatusEqual(),
        query.precipitationTypeEqual()
    );

    return new CursorResponse<>(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        query.sortBy(),
        query.sortDirection()
    );
  }

  private record FeedListQuery(
      int limit,
      String sortBy,
      String sortDirection,
      String cursor,
      UUID idAfter,
      String keywordLike,
      UUID authorIdEqual,
      String skyStatusEqual,
      String precipitationTypeEqual,
      UUID viewerId
  ) {
    boolean isLikeCountSort() {
      return "likeCount".equals(sortBy);
    }
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
