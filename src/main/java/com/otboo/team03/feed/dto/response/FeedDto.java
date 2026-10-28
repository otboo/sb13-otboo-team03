package com.otboo.team03.feed.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FeedDto(
    UUID id,
    Instant createdAt,
    Instant updatedAt,
    AuthorDto author,
    WeatherSummaryDto weather,
    List<OotdDto> ootds,
    String content,
    long likeCount,
    int commentCount,
    boolean likedByMe
) {
}
