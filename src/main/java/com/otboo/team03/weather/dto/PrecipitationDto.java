package com.otboo.team03.weather.dto;

import com.otboo.team03.weather.entity.PrecipitationType;

public record PrecipitationDto(
    PrecipitationType type,
    Double amount,
    Double probability
) {}