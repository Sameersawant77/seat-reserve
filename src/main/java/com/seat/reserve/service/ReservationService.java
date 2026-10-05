package com.seat.reserve.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.seat.reserve.api.ApiException;
import com.seat.reserve.api.ErrorCode;
import com.seat.reserve.auth.TokenService;
import com.seat.reserve.domain.IdempotencyRecord;
import com.seat.reserve.domain.ReservationRecord;
import com.seat.reserve.domain.ReservationStatus;
import com.seat.reserve.domain.SeatRecord;
import com.seat.reserve.domain.ShowRecord;
import com.seat.reserve.metrics.ReservationMetrics;
import com.seat.reserve.repo.IdempotencyRepository;
import com.seat.reserve.repo.ReservationRepository;
import com.seat.reserve.repo.SeatRepository;
import com.seat.reserve.repo.ShowRepository;
import com.seat.reserve.web.dto.ReservationResponse;
import com.seat.reserve.web.dto.ReserveRequest;

@Service
public class ReservationService {

	private final ShowRepository showRepository;
	private final SeatRepository seatRepository;
	private final ReservationRepository reservationRepository;
	private final IdempotencyRepository idempotencyRepository;
	private final ReservationMetrics metrics;
	private final ShowService showService;

	public ReservationService(
			ShowRepository showRepository,
			SeatRepository seatRepository,
			ReservationRepository reservationRepository,
			IdempotencyRepository idempotencyRepository,
			ReservationMetrics metrics,
			ShowService showService) {
		this.showRepository = showRepository;
		this.seatRepository = seatRepository;
		this.reservationRepository = reservationRepository;
		this.idempotencyRepository = idempotencyRepository;
		this.metrics = metrics;
		this.showService = showService;
	}

	@Transactional
	public ReservationResponse reserve(UUID showId, String userId, ReserveRequest request, String idempotencyKey) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new ApiException(ErrorCode.BAD_REQUEST, "idempotency_key is required");
		}
		ShowRecord show = showRepository.findById(showId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Show not found"));

		List<String> seats = normalizeSeats(request.seats());
		String requestHash = TokenService.hashRequest(showId + "|" + String.join(",", seats));

		boolean inserted = idempotencyRepository.tryInsertInProgress(userId, idempotencyKey, requestHash);
		if (!inserted) {
			return handleIdempotencyConflict(userId, idempotencyKey, requestHash);
		}

		try {
			Instant now = Instant.now();
			List<SeatRecord> locked = seatRepository.lockSeatsForUpdate(showId, seats);
			if (locked.size() != seats.size()) {
				throw new ApiException(ErrorCode.SEAT_TAKEN, "One or more seats do not exist");
			}
			for (SeatRecord seat : locked) {
				if (!seat.isBookable(now)) {
					throw new ApiException(ErrorCode.SEAT_TAKEN, "Seat already taken: " + seat.label());
				}
			}

			int active = seatRepository.countActiveSeatsForUser(showId, userId, now);
			if (active + seats.size() > show.perUserLimit()) {
				throw new ApiException(ErrorCode.PER_USER_LIMIT, "Per-user seat limit exceeded");
			}

			UUID reservationId = UUID.randomUUID();
			long amount = show.pricePaise() * seats.size();
			ReservationRecord reservation = new ReservationRecord(
					reservationId, showId, userId, ReservationStatus.HELD, amount);
			reservationRepository.insert(reservation);

			Instant expiresAt = now.plusSeconds(show.holdTtlSeconds());
			for (SeatRecord seat : locked) {
				int updated = seatRepository.markHeld(seat.id(), reservationId, userId, expiresAt, now);
				if (updated != 1) {
					throw new ApiException(ErrorCode.SEAT_TAKEN, "Seat already taken: " + seat.label());
				}
			}

			idempotencyRepository.complete(userId, idempotencyKey, reservationId);
			metrics.recordHeld();
			showService.getShow(showId);
			return toResponse(reservation, seats);
		} catch (ApiException ex) {
			metrics.recordDecline(ex.code());
			idempotencyRepository.deleteInProgress(userId, idempotencyKey);
			throw ex;
		}
	}

	private ReservationResponse handleIdempotencyConflict(String userId, String key, String requestHash) {
		IdempotencyRecord existing = idempotencyRepository.find(userId, key)
				.orElseThrow(() -> new ApiException(ErrorCode.SEAT_TAKEN, "Idempotency conflict"));
		if (!existing.requestHash().equals(requestHash)) {
			metrics.recordDecline(ErrorCode.IDEMPOTENCY_DIFFERENT_BODY);
			throw new ApiException(ErrorCode.IDEMPOTENCY_DIFFERENT_BODY, "Idempotency key reused with different request");
		}
		if ("in_progress".equals(existing.status())) {
			throw new ApiException(ErrorCode.IDEMPOTENCY_IN_PROGRESS, "Request with this idempotency key is in progress");
		}
		ReservationRecord reservation = reservationRepository.findById(existing.reservationId())
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Reservation not found for idempotency key"));
		metrics.recordIdempotentReplay();
		List<String> labels = seatRepository.labelsForReservation(reservation.id());
		return toResponse(reservation, labels);
	}

	@Transactional
	public ReservationResponse confirm(UUID reservationId, String userId) {
		ReservationRecord reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Reservation not found"));
		if (!reservation.userId().equals(userId)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Not reservation owner");
		}
		if (reservation.status() != ReservationStatus.HELD) {
			throw new ApiException(ErrorCode.INVALID_STATE, "Reservation is not held");
		}
		int updated = seatRepository.confirmSeats(reservationId, userId);
		List<String> labels = seatRepository.labelsForReservation(reservationId);
		if (updated != labels.size()) {
			throw new ApiException(ErrorCode.INVALID_STATE, "Hold expired or seats unavailable");
		}
		reservationRepository.updateStatus(reservationId, ReservationStatus.CONFIRMED);
		metrics.recordConfirmed();
		showService.getShow(reservation.showId());
		ReservationRecord confirmed = new ReservationRecord(
				reservation.id(), reservation.showId(), reservation.userId(),
				ReservationStatus.CONFIRMED, reservation.amountPaise());
		return toResponse(confirmed, labels);
	}

	@Transactional
	public ReservationResponse cancel(UUID reservationId, String userId) {
		ReservationRecord reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Reservation not found"));
		if (!reservation.userId().equals(userId)) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Not reservation owner");
		}
		if (reservation.status() != ReservationStatus.HELD) {
			throw new ApiException(ErrorCode.INVALID_STATE, "Only held reservations can be cancelled");
		}
		seatRepository.releaseSeats(reservationId, userId);
		reservationRepository.updateStatus(reservationId, ReservationStatus.CANCELLED);
		showService.getShow(reservation.showId());
		List<String> labels = seatRepository.labelsForReservation(reservationId);
		ReservationRecord cancelled = new ReservationRecord(
				reservation.id(), reservation.showId(), reservation.userId(),
				ReservationStatus.CANCELLED, reservation.amountPaise());
		return toResponse(cancelled, labels);
	}

	private static List<String> normalizeSeats(List<String> seats) {
		if (seats == null || seats.isEmpty()) {
			throw new ApiException(ErrorCode.BAD_REQUEST, "At least one seat is required");
		}
		List<String> copy = new ArrayList<>(seats);
		copy.sort(Comparator.naturalOrder());
		for (int i = 1; i < copy.size(); i++) {
			if (copy.get(i).equals(copy.get(i - 1))) {
				throw new ApiException(ErrorCode.BAD_REQUEST, "Duplicate seats in request");
			}
		}
		return copy;
	}

	private ReservationResponse toResponse(ReservationRecord reservation, List<String> seats) {
		return new ReservationResponse(
				reservation.id(),
				reservation.showId(),
				reservation.userId(),
				seats,
				reservation.amountPaise(),
				reservation.status().name().toLowerCase());
	}
}
