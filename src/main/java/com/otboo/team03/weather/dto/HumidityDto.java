package com.otboo.team03.weather.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "습도 정보")
public record HumidityDto(
    @Schema(description = "현재 습도") Double current,
    @Schema(description = "전날 대비 습도 변동") Double comparedToDayBefore
) {}