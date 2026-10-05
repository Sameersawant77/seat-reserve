package com.seat.reserve.web.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ShowResponse(
		UUID id,
		String name,
		long pricePaise,
		int perUserLimit,
		List<ShowSeatView> seats,
		Map<String, Integer> counts,
		boolean reconciled) {
}
