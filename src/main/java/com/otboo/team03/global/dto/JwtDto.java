package com.otboo.team03.global.dto;

import com.otboo.team03.user.dto.response.UserDto;

public record JwtDto(
        UserDto userDto,
        String accessToken
) {
}
