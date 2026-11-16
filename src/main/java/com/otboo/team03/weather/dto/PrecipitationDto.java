package com.otboo.team03.weather.dto;

import com.otboo.team03.weather.entity.PrecipitationType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "강수량 정보")
public record PrecipitationDto(
    @Schema(description = "강수 타입") PrecipitationType type,
    @Schema(description = "강수량") Double amount,
    @Schema(description = "강수 확률") Double probability
) {}