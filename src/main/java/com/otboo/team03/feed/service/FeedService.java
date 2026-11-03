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
import com.otboo.team03.global.exception.ErrorCode;
import com.otboo.team03.global.exception.OtbooException;
import com.otboo.team03.user.repository.UserRepository;
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
