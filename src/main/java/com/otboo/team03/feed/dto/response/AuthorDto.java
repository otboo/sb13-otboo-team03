package com.otboo.team03.feed.dto.response;

import java.util.UUID;

public record AuthorDto(
    UUID userId,
    String name,
    String profileImageUrl
) {
}
