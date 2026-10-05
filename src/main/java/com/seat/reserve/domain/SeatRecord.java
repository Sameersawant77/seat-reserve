package com.seat.reserve.domain;

import java.time.Instant;
import java.util.UUID;

public record SeatRecord(
		UUID id,
		UUID showId,
		String label,
		SeatStatus status,
		UUID reservationId,
		String heldBy,
		Instant holdExpiresAt) {

	public boolean isBookable(Instant now) {
		if (status == SeatStatus.AVAILABLE) {
			return true;
		}
		if (status == SeatStatus.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now)) {
			return true;
		}
		return false;
	}

	public SeatStatus effectiveStatus(Instant now) {
		if (status == SeatStatus.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now)) {
			return SeatStatus.AVAILABLE;
		}
		return status;
	}
}
