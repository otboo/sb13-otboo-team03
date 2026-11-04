package com.otboo.team03.profile.dto;

import java.util.List;

public record WeatherAPILocation(
    Double latitude,
    Double longitude,
    Integer x,
    Integer y,
    List<String> locationNames
) {
}
