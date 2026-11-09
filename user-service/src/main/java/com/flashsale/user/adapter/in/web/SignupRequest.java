package com.flashsale.user.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record SignupRequest(@NotBlank String email, @NotBlank String password, @NotBlank String name) {
}
