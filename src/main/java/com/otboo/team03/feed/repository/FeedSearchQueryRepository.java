package com.otboo.team03.feed.repository;

import com.otboo.team03.feed.entity.Feed;
import com.otboo.team03.feed.entity.QFeed;
import com.otboo.team03.feed.entity.QFeedLike;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class FeedSearchQueryRepository {

  private final JPAQueryFactory queryFactory;

  public FeedSearchQueryRepository(EntityManager entityManager) {
    this.queryFactory = new JPAQueryFactory(entityManager);
  }

  public List<Feed> findByFilters(
      String keywordLike,
      UUID authorIdEqual,
      String sortBy,
      String sortDirection,
      Instant createdAtCursor,
      Long likeCountCursor,
      UUID idAfter,
      int pageSize
  ) {
    QFeed feed = QFeed.feed;
    boolean ascending = "ASCENDING".equals(sortDirection);

    JPAQuery<Feed> query = queryFactory
        .selectFrom(feed)
        .where(
            keywordCondition(feed, keywordLike),
            authorCondition(feed, authorIdEqual)
        );

    if ("likeCount".equals(sortBy)) {
      QFeedLike feedLike = QFeedLike.feedLike;
      NumberExpression<Long> likeCount = feedLike.id.count();

      query.leftJoin(feedLike)
          .on(feedLike.feedId.eq(feed.id))
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

    } else {
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
    }

    return query.limit(pageSize).fetch();
  }

  public long countByFilters(
      String keywordLike,
      UUID authorIdEqual
  ) {
    QFeed feed = QFeed.feed;

    Long count = queryFactory
        .select(feed.count())
        .from(feed)
        .where(
            keywordCondition(feed, keywordLike),
            authorCondition(feed, authorIdEqual)
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
}
