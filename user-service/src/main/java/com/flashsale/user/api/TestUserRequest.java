package com.flashsale.user.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TestUserRequest(@NotNull @Min(1) @Max(50000) Integer count) {
}
