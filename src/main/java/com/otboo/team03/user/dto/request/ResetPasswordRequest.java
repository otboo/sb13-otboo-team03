package com.otboo.team03.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(

        @NotBlank
        @Email
        String email

) {
}
