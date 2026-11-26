package com.otboo.team03.user.dto.request;

import jakarta.validation.constraints.NotNull;

public record UserLockUpdateRequest(

        @NotNull
        Boolean locked
) {
}
