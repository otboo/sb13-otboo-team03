package com.otboo.team03.feed.dto.request;

import java.util.UUID;

public record CommentCreateRequest(
    UUID feedId,
    UUID authorId,
    String content
) {
}
