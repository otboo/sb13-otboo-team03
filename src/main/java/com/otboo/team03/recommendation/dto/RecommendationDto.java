package com.otboo.team03.recommendation.dto;

import com.otboo.team03.feed.dto.response.OotdDto;

import java.util.List;
import java.util.UUID;

public record RecommendationDto(
        UUID weatherId,
        UUID userId,
        List<OotdDto> clothes
) {
}
