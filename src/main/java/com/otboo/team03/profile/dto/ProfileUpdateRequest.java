package com.otboo.team03.profile.dto;

import java.time.LocalDate;
import org.hibernate.validator.constraints.Range;

public record ProfileUpdateRequest(
    String name,
    Gender gender,
    LocalDate birthDate,
    WeatherAPILocation location,

    @Range(min = 1, max = 5, message = "온도 민감도는 1에서 5 사이여야 합니다.")
    Integer temperatureSensitivity
) {

}
