package com.seat.reserve.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seat.reserve.api.ApiException;
import com.seat.reserve.api.ErrorCode;
import com.seat.reserve.config.ReserveProperties;
import com.seat.reserve.domain.SeatRecord;
import com.seat.reserve.domain.SeatStatus;
import com.seat.reserve.domain.ShowRecord;
import com.seat.reserve.metrics.ReservationMetrics;
import com.seat.reserve.repo.SeatRepository;
import com.seat.reserve.repo.ShowRepository;
import com.seat.reserve.web.dto.CreateShowRequest;
import com.seat.reserve.web.dto.ShowResponse;
import com.seat.reserve.web.dto.ShowSeatView;

@Service
public class ShowService {

	private final ShowRepository showRepository;
	private final SeatRepository seatRepository;
	private final ReserveProperties properties;
	private final ReservationMetrics metrics;

	public ShowService(
			ShowRepository showRepository,
			SeatRepository seatRepository,
			ReserveProperties properties,
			ReservationMetrics metrics) {
		this.showRepository = showRepository;
		this.seatRepository = seatRepository;
		this.properties = properties;
		this.metrics = metrics;
	}

	@Transactional
	public ShowResponse createShow(CreateShowRequest request) {
		UUID id = UUID.randomUUID();
		List<String> labels = request.seats().stream().distinct().sorted().toList();
		if (labels.isEmpty()) {
			throw new ApiException(ErrorCode.BAD_REQUEST, "At least one seat is required");
		}
		ShowRecord show = new ShowRecord(
				id,
				request.name(),
				request.pricePaise(),
				properties.hold().defaultPerUserLimit(),
				properties.hold().defaultTtlSeconds());
		showRepository.insert(show);
		seatRepository.insertBatch(id, labels);
		metrics.updateSeatsAvailable(id, labels.size());
		return getShow(id);
	}

	public ShowResponse getShow(UUID showId) {
		ShowRecord show = showRepository.findById(showId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Show not found"));
		Instant now = Instant.now();
		List<SeatRecord> seats = seatRepository.findByShowId(showId);
		Map<String, String> seatMap = new LinkedHashMap<>();
		int available = 0;
		int held = 0;
		int confirmed = 0;
		for (SeatRecord seat : seats) {
			SeatStatus effective = seat.effectiveStatus(now);
			seatMap.put(seat.label(), effective.name().toLowerCase());
			switch (effective) {
				case AVAILABLE -> available++;
				case HELD -> held++;
				case CONFIRMED -> confirmed++;
			}
		}
		metrics.updateSeatsAvailable(showId, available);
		int total = seats.size();
		return new ShowResponse(
				show.id(),
				show.name(),
				show.pricePaise(),
				show.perUserLimit(),
				seatMap.entrySet().stream()
						.map(e -> new ShowSeatView(e.getKey(), e.getValue()))
						.collect(Collectors.toList()),
				Map.of(
						"available", available,
						"held", held,
						"confirmed", confirmed,
						"total", total),
				available + held + confirmed == total);
	}
}
