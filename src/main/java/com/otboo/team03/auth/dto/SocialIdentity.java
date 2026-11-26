package com.otboo.team03.auth.dto;

import com.otboo.team03.auth.entity.SocialProvider;

public record SocialIdentity(
        SocialProvider provider,
        String providerUserId,
        String email,
        String name,
        boolean emailVerified
) {
}