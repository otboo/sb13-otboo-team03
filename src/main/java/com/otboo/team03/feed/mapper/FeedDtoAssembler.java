package com.otboo.team03.feed.mapper;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.entity.Clothes;
import com.otboo.team03.feed.dto.response.CommentDto;
import com.otboo.team03.feed.dto.response.WeatherSummaryDto;
import com.otboo.team03.feed.entity.FeedComment;
import com.otboo.team03.feed.repository.FeedBatchQueryRepository;
import com.otboo.team03.global.storage.ImageStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import com.otboo.team03.feed.dto.response.AuthorDto;
import com.otboo.team03.feed.dto.response.FeedDto;
import com.otboo.team03.feed.dto.response.OotdDto;
import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.repository.FeedClothesRepository;
import com.otboo.team03.feed.repository.FeedWeatherLookupRepository;
import com.otboo.team03.user.entity.User;
import com.otboo.team03.user.repository.UserRepository;
import com.otboo.team03.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FeedDtoAssembler {

  private final UserRepository userRepository;
  private final FeedClothesRepository feedClothesRepository;
  private final FeedWeatherLookupRepository weatherRepository;
  private final JdbcTemplate jdbcTemplate;
  private final ObjectProvider<ImageStorage> imageStorageProvider;
  private final FeedBatchQueryRepository batchQueryRepository;

  public FeedDto toDto(Feed feed, UUID viewerId) {
    AuthorDto authorDto = toAuthorDto(feed.getAuthorId());

    List<OotdDto> ootds = feedClothesRepository
        .findAllByFeed_Id(feed.getId())
        .stream()
        .map(link -> toOotdDto(link.getClothes()))
        .toList();

    Long likeCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feed_likes WHERE feed_id = ?",
        Long.class,
        feed.getId()
    );

    Integer commentCount = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM feed_comments WHERE feed_id = ?",
        Integer.class,
        feed.getId()
    );

    boolean likedByMe = false;

    if (viewerId != null) {
      Boolean liked = jdbcTemplate.queryForObject(
          """
          SELECT EXISTS (
              SELECT 1
              FROM feed_likes
              WHERE feed_id = ? AND user_id = ?
          )
          """,
          Boolean.class,
          feed.getId(),
          viewerId
      );

      likedByMe = Boolean.TRUE.equals(liked);
    }

    return new FeedDto(
        feed.getId(),
        feed.getCreatedAt(),
        feed.getUpdatedAt(),
        authorDto,
        weatherRepository.findSummaryById(feed.getWeatherId()),
        ootds,
        feed.getContent(),
        likeCount,
        commentCount,
        likedByMe
    );
  }
  public List<FeedDto> toDtos(List<Feed> feeds, UUID viewerId) {
    if (feeds.isEmpty()) {
      return List.of();
    }

    Set<UUID> feedIds = new HashSet<>();
    Set<UUID> authorIds = new HashSet<>();
    Set<UUID> weatherIds = new HashSet<>();
    for (Feed feed : feeds) {
      feedIds.add(feed.getId());
      authorIds.add(feed.getAuthorId());
      weatherIds.add(feed.getWeatherId());
    }

    Map<UUID, AuthorDto> authors = batchQueryRepository.findAuthors(authorIds);
    Map<UUID, WeatherSummaryDto> weathers = batchQueryRepository.findWeathers(weatherIds);
    Map<UUID, FeedBatchQueryRepository.FeedCounts> counts =
        batchQueryRepository.findCounts(feedIds);
    Set<UUID> likedFeedIds = batchQueryRepository.findLikedFeedIds(feedIds, viewerId);

    List<FeedBatchQueryRepository.ClothesRow> clothesRows =
        batchQueryRepository.findClothes(feedIds);
    Set<UUID> clothesIds = new HashSet<>();
    for (FeedBatchQueryRepository.ClothesRow row : clothesRows) {
      clothesIds.add(row.clothesId());
    }
    Map<UUID, List<ClothesAttributeWithDefDto>> attributes =
        batchQueryRepository.findClothesAttributes(clothesIds);

    Map<UUID, List<OotdDto>> ootdsByFeedId = new HashMap<>();
    for (FeedBatchQueryRepository.ClothesRow row : clothesRows) {
      String imageUrl = row.imageKey() == null
          ? row.imageUrl()
          : imageStorageProvider.getObject().getAccessUrl(row.imageKey());

      ootdsByFeedId.computeIfAbsent(row.feedId(), ignored -> new ArrayList<>())
          .add(new OotdDto(
              row.clothesId(),
              row.name(),
              imageUrl,
              row.type(),
              attributes.getOrDefault(row.clothesId(), List.of())
          ));
    }

    return feeds.stream().map(feed -> {
      AuthorDto author = authors.get(feed.getAuthorId());
      if (author == null) {
        throw new UserNotFoundException();
      }
      WeatherSummaryDto weather = Objects.requireNonNull(
          weathers.get(feed.getWeatherId()), "Weather not found"
      );
      FeedBatchQueryRepository.FeedCounts aggregate =
          counts.getOrDefault(feed.getId(),
              new FeedBatchQueryRepository.FeedCounts(0L, 0));

      return new FeedDto(
          feed.getId(),
          feed.getCreatedAt(),
          feed.getUpdatedAt(),
          author,
          weather,
          ootdsByFeedId.getOrDefault(feed.getId(), List.of()),
          feed.getContent(),
          aggregate.likeCount(),
          aggregate.commentCount(),
          likedFeedIds.contains(feed.getId())
      );
    }).toList();
  }

  private String findProfileImageUrl(UUID userId) {
    return jdbcTemplate.query(
        """
        SELECT profile_image_url
        FROM profiles
        WHERE user_id = ?
        """,
        rs -> rs.next()
            ? rs.getString("profile_image_url")
            : null,
        userId
    );
  }

  private OotdDto toOotdDto(Clothes clothes) {
    List<ClothesAttributeWithDefDto> attributes =
        findClothesAttributes(clothes.getId());

    return new OotdDto(
        clothes.getId(),
        clothes.getName(),
        clothes.getImageKey() == null ? clothes.getImageUrl()
            : imageStorageProvider.getObject().getAccessUrl(clothes.getImageKey()),
        clothes.getType(),
        attributes
    );
  }

  private List<ClothesAttributeWithDefDto> findClothesAttributes(
      UUID clothesId
  ) {
    return jdbcTemplate.query("""
                SELECT
                    ca.definition_id,
                    cad.name AS definition_name,
                    cad.selectable_values,
                    ca.value
                FROM clothes_attribute ca
                JOIN clothes_attribute_definitions cad
                    ON ca.definition_id = cad.id
                WHERE ca.clothes_id = ?
                ORDER BY ca.definition_id
                """,
        (rs, rowNum) -> {
          String[] values = (String[]) rs
              .getArray("selectable_values")
              .getArray();

          return new ClothesAttributeWithDefDto(
              rs.getObject("definition_id", UUID.class),
              rs.getString("definition_name"),
              Arrays.asList(values),
              rs.getString("value")
          );
        },
        clothesId
    );
  }

  public List<CommentDto> toCommentDtos(List<FeedComment> comments) {
    if (comments.isEmpty()) {
      return List.of();
    }

    Set<UUID> authorIds = new HashSet<>();

    for (FeedComment comment : comments) {
      authorIds.add(comment.getAuthorId());
    }

    Map<UUID, AuthorDto> authors =
        batchQueryRepository.findAuthors(authorIds);

    return comments.stream()
        .map(comment -> {
          AuthorDto author = authors.get(comment.getAuthorId());

          if (author == null) {
            throw new UserNotFoundException();
          }

          return new CommentDto(
              comment.getId(),
              comment.getCreatedAt(),
              comment.getFeedId(),
              author,
              comment.getContent()
          );
        })
        .toList();
  }

  public CommentDto toCommentDto(FeedComment comment) {
    return new CommentDto(
        comment.getId(),
        comment.getCreatedAt(),
        comment.getFeedId(),
        toAuthorDto(comment.getAuthorId()),
        comment.getContent()
    );
  }

  private AuthorDto toAuthorDto(UUID authorId) {
    User author = userRepository.findById(authorId)
        .orElseThrow(UserNotFoundException::new);

    return new AuthorDto(
        author.getId(),
        author.getName(),
        findProfileImageUrl(author.getId())
    );
  }
}
