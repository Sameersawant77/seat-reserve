package com.seat.reserve.web.dto;

import com.seat.reserve.auth.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TokenRequest(
		@NotBlank String userId,
		@NotNull Role role,
		Long ttlSeconds) {
}
