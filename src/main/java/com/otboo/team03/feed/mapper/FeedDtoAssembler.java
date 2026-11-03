package com.otboo.team03.feed.mapper;

import com.otboo.team03.clothes.dto.response.ClothesAttributeWithDefDto;
import com.otboo.team03.clothes.entity.Clothes;
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

  public FeedDto toDto(Feed feed, UUID viewerId) {
    User author = userRepository.findById(feed.getAuthorId())
        .orElseThrow(UserNotFoundException::new);

    AuthorDto authorDto = new AuthorDto(
        author.getId(),
        author.getName(),
        findProfileImageUrl(author.getId())
    );

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
        clothes.getImageUrl(),
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
}
