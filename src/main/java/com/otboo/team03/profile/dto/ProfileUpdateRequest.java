package com.otboo.team03.profile.dto;

import java.time.LocalDate;

public record ProfileUpdateRequest(
    String name,
    Gender gender,
    LocalDate birthDate,
    WeatherAPILocation location,
    Integer temperatureSensitivity
) {

}
