package com.seat.reserve.domain;

import java.util.UUID;

public record ReservationRecord(
		UUID id,
		UUID showId,
		String userId,
		ReservationStatus status,
		long amountPaise) {
}
