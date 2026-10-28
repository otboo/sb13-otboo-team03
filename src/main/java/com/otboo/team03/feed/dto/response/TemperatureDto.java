package com.otboo.team03.feed.dto.response;

public record TemperatureDto(
    Double current,
    Double comparedToDayBefore,
    Double min,
    Double max
) {
}
