package com.seat.reserve.domain;

import java.util.UUID;

public record ShowRecord(
		UUID id,
		String name,
		long pricePaise,
		int perUserLimit,
		int holdTtlSeconds) {
}
