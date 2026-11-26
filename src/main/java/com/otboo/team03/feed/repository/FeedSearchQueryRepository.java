package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.QFeed;
import com.otboo.team03.feed.entity.QFeedLike;
import com.otboo.team03.feed.entity.QFeedWeatherFilter;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class FeedSearchQueryRepository {

  private final JPAQueryFactory queryFactory;

  public record FeedSearchResult(
      Feed feed,
      Long sortLikeCount
  ) {
  }

  public FeedSearchQueryRepository(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  public List<FeedSearchResult> findByFilters(
      String keywordLike,
      UUID authorIdEqual,
      String skyStatusEqual,
      String precipitationTypeEqual,
      String sortBy,
      String sortDirection,
      Instant createdAtCursor,
      Long likeCountCursor,
      UUID idAfter,
      int pageSize
  ) {
    QFeed feed = QFeed.feed;
    boolean ascending = "ASCENDING".equals(sortDirection);

    BooleanExpression keywordFilter =
        keywordCondition(feed, keywordLike);

    BooleanExpression authorFilter =
        authorCondition(feed, authorIdEqual);

    BooleanExpression weatherFilter = weatherCondition(
        feed,
        skyStatusEqual,
        precipitationTypeEqual
    );

    if ("likeCount".equals(sortBy)) {
      QFeedLike feedLike = QFeedLike.feedLike;
      NumberExpression<Long> likeCount = feedLike.id.count();

      JPAQuery<Tuple> query = queryFactory
          .select(feed, likeCount)
          .from(feed)
          .leftJoin(feedLike)
          .on(feedLike.feedId.eq(feed.id))
          .where(keywordFilter, authorFilter, weatherFilter)
          .groupBy(feed.id);

      if (likeCountCursor != null) {
        query.having(
            ascending
                ? likeCount.gt(likeCountCursor)
                .or(
                    likeCount.eq(likeCountCursor)
                        .and(feed.id.gt(idAfter))
                )
                : likeCount.lt(likeCountCursor)
                    .or(
                        likeCount.eq(likeCountCursor)
                            .and(feed.id.lt(idAfter))
                    )
        );
      }

      query.orderBy(
          ascending ? likeCount.asc() : likeCount.desc(),
          ascending ? feed.id.asc() : feed.id.desc()
      );

      return query.limit(pageSize)
          .fetch()
          .stream()
          .map(row -> new FeedSearchResult(
              row.get(feed),
              row.get(likeCount)
          ))
          .toList();
    }

    JPAQuery<Feed> query = queryFactory
        .selectFrom(feed)
        .where(keywordFilter, authorFilter, weatherFilter);

    if (createdAtCursor != null) {
      query.where(
          ascending
              ? feed.createdAt.gt(createdAtCursor)
              .or(
                  feed.createdAt.eq(createdAtCursor)
                      .and(feed.id.gt(idAfter))
              )
              : feed.createdAt.lt(createdAtCursor)
                  .or(
                      feed.createdAt.eq(createdAtCursor)
                          .and(feed.id.lt(idAfter))
                  )
      );
    }

    query.orderBy(
        ascending ? feed.createdAt.asc() : feed.createdAt.desc(),
        ascending ? feed.id.asc() : feed.id.desc()
    );

    return query.limit(pageSize)
        .fetch()
        .stream()
        .map(result -> new FeedSearchResult(result, null))
        .toList();
  }

  public long countByFilters(
      String keywordLike,
      UUID authorIdEqual,
      String skyStatusEqual,
      String precipitationTypeEqual
  ) {
    QFeed feed = QFeed.feed;

    Long count = queryFactory
        .select(feed.count())
        .from(feed)
        .where(
            keywordCondition(feed, keywordLike),
            authorCondition(feed, authorIdEqual),
            weatherCondition(
                feed,
                skyStatusEqual,
                precipitationTypeEqual
            )
        )
        .fetchOne();

    return count == null ? 0L : count;
  }

  private BooleanExpression keywordCondition(
      QFeed feed,
      String keywordLike
  ) {
    if (keywordLike == null || keywordLike.isBlank()) {
      return null;
    }

    return feed.content.containsIgnoreCase(keywordLike.trim());
  }

  private BooleanExpression authorCondition(
      QFeed feed,
      UUID authorIdEqual
  ) {
    if (authorIdEqual == null) {
      return null;
    }

    return feed.authorId.eq(authorIdEqual);
  }

  private BooleanExpression weatherCondition(
      QFeed feed,
      String skyStatusEqual,
      String precipitationTypeEqual
  ) {
    if (skyStatusEqual == null && precipitationTypeEqual == null) {
      return null;
    }

    QFeedWeatherFilter weather = QFeedWeatherFilter.feedWeatherFilter;

    return JPAExpressions
        .selectOne()
        .from(weather)
        .where(
            weather.id.eq(feed.weatherId),
            skyStatusEqual == null
                ? null
                : weather.skyStatus.eq(skyStatusEqual),
            precipitationTypeEqual == null
                ? null
                : weather.precipitationType.eq(precipitationTypeEqual)
        )
        .exists();
  }
}
