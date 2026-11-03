package com.otboo.team03.user.init;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.admin")
public record AdminProperties(
        String email,
        String password,
        String name
) {
}
