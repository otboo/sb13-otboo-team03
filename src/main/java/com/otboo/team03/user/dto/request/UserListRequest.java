package com.otboo.team03.user.dto.request;

import com.otboo.team03.user.entity.UserRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UserListRequest(

        String cursor,
        UUID idAfter,

        @NotNull
        @Min(1)
        @Max(100)
        Integer limit,

        @NotNull
        String sortBy,

        @NotNull
        String sortDirection,

        String emailLike,
        UserRole roleEqual,
        Boolean locked
) {
}
