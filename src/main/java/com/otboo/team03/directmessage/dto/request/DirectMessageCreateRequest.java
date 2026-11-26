package com.otboo.team03.directmessage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DirectMessageCreateRequest(
        @NotNull UUID receiverId,
        @NotBlank @Size(max = 1_000) String content
) {
}
