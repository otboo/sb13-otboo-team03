package com.otboo.team03.weather.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "풍속 정보")
public record WindSpeedDto(
    @Schema(description = "풍속") Double speed,
    @Schema(description = "정성 정보") String asWord // WEAK, MODERATE, STRONG
) {}