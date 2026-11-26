package com.otboo.team03.user.dto.request;

import com.otboo.team03.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(

        @NotNull
        UserRole role

) {
}
