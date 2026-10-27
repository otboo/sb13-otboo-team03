package com.otboo.team03.user.dto.response;

import com.otboo.team03.user.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        Instant createdAt,
        String email,
        String name,
        UserRole role,
        boolean locked
) {
}
