package com.seat.reserve.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seat.reserve.auth.AuthContext;
import com.seat.reserve.service.ReservationService;
import com.seat.reserve.web.dto.ReservationResponse;
import com.seat.reserve.web.dto.ReserveRequest;

import jakarta.validation.Valid;

@RestController
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping("/shows/{id}/reserve")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse reserve(
			@PathVariable("id") UUID showId,
			@Valid @RequestBody ReserveRequest request,
			@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader) {
		String userId = AuthContext.require().userId();
		String key = idempotencyHeader != null && !idempotencyHeader.isBlank()
				? idempotencyHeader
				: request.idempotencyKey();
		return reservationService.reserve(showId, userId, request, key);
	}

	@PostMapping("/reservations/{id}/confirm")
	public ReservationResponse confirm(@PathVariable("id") UUID reservationId) {
		return reservationService.confirm(reservationId, AuthContext.require().userId());
	}

	@PostMapping("/reservations/{id}/cancel")
	public ReservationResponse cancel(@PathVariable("id") UUID reservationId) {
		return reservationService.cancel(reservationId, AuthContext.require().userId());
	}
}
