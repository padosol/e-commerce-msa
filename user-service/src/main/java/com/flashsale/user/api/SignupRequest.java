package com.flashsale.user.api;

import jakarta.validation.constraints.NotBlank;

public record SignupRequest(@NotBlank String email, @NotBlank String password, @NotBlank String name) {
}
