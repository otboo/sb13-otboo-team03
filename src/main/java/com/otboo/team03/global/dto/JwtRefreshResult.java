package com.otboo.team03.global.dto;

public record JwtRefreshResult(
        JwtDto jwtDto,
        String refreshToken
) {
}
