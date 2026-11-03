package com.otboo.team03.feed.dto.response;

public record PrecipitationDto(
    String type,
    Double amount,
    Double probability
) {
}
