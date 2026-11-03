package com.otboo.team03.feed.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record FeedCreateRequest(
    @NotNull UUID authorId,
    @NotNull UUID weatherId,
    List<UUID> clothesIds,
    @NotBlank String content
) {
}
