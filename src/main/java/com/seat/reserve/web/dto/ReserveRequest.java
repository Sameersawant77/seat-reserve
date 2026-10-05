package com.seat.reserve.web.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record ReserveRequest(
		@NotEmpty List<String> seats,
		String idempotencyKey) {
}
