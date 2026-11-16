package com.otboo.team03.weather.dto;

public record WindSpeedDto(
    Double speed,
    String asWord // WEAK, MODERATE, STRONG
) {}