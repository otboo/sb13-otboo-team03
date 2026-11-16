package com.otboo.team03.weather.dto;

public record HumidityDto(
    Double current,
    Double comparedToDayBefore
) {}