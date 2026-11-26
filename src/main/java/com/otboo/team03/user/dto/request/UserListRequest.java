package com.otboo.team03.user.dto.request;

import com.otboo.team03.user.entity.UserRole;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record UserListRequest(

        String cursor,
        UUID idAfter,

        @NotNull
        @Min(1)
        @Max(100)
        Integer limit,

        @NotNull
        @Pattern(regexp = "email|createdAt")
        String sortBy,

        @NotNull
        @Pattern(regexp = "ASCENDING|DESCENDING")
        String sortDirection,

        String emailLike,
        UserRole roleEqual,
        Boolean locked
) {
    public boolean isDefaultFirstPage() {
        return cursor == null
                && idAfter == null
                && (emailLike == null || emailLike.isBlank())
                && roleEqual == null
                && locked == null;
    }
}
