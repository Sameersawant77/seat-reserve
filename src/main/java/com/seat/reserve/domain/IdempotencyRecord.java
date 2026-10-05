package com.seat.reserve.domain;

import java.util.UUID;

public record IdempotencyRecord(
		String userId,
		String key,
		String requestHash,
		UUID reservationId,
		String status) {
}
