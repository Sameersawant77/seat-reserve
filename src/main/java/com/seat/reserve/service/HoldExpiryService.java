package com.seat.reserve.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seat.reserve.repo.ReservationRepository;
import com.seat.reserve.repo.SeatRepository;

@Service
public class HoldExpiryService {

	private static final Logger log = LoggerFactory.getLogger(HoldExpiryService.class);

	private final SeatRepository seatRepository;
	private final ReservationRepository reservationRepository;
	private final ShowService showService;

	public HoldExpiryService(
			SeatRepository seatRepository,
			ReservationRepository reservationRepository,
			ShowService showService) {
		this.seatRepository = seatRepository;
		this.reservationRepository = reservationRepository;
		this.showService = showService;
	}

	@Scheduled(fixedDelayString = "${reserve.hold.sweep-interval-ms:30000}")
	@Transactional
	public void sweepExpiredHolds() {
		Instant now = Instant.now();
		int released = seatRepository.expireHeldSeats(now);
		List<UUID> reservationIds = seatRepository.findHeldReservationsWithoutHeldSeats();
		int expired = reservationRepository.markExpiredBatch(reservationIds);
		if (released > 0 || expired > 0) {
			log.info("Expired holds: seatsReleased={}, reservationsExpired={}", released, expired);
		}
		for (UUID reservationId : reservationIds) {
			reservationRepository.findById(reservationId).ifPresent(r -> showService.getShow(r.showId()));
		}
	}
}
