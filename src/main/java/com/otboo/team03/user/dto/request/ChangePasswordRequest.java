package com.otboo.team03.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(

        @NotBlank
        String password

) {
}
