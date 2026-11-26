package com.otboo.team03.feed.dto.response;

import java.time.Instant;
import java.util.UUID;

public record CommentDto(
    UUID id,
    Instant createdAt,
    UUID feedId,
    AuthorDto author,
    String content
) {
}
