package com.seat.reserve.metrics;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.seat.reserve.api.ErrorCode;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class ReservationMetrics {

	private final MeterRegistry registry;
	private final Counter confirmed;
	private final Counter held;
	private final Counter idempotentReplay;
	private final Counter declinedSeatTaken;
	private final Counter declinedPerUserLimit;
	private final Counter declinedDifferentBody;
	private final ConcurrentHashMap<UUID, AtomicInteger> seatsAvailable = new ConcurrentHashMap<>();

	public ReservationMetrics(MeterRegistry registry) {
		this.registry = registry;
		this.confirmed = Counter.builder("reservations_confirmed_total").register(registry);
		this.held = Counter.builder("reservations_held_total").register(registry);
		this.idempotentReplay = Counter.builder("idempotent_replay_total").register(registry);
		this.declinedSeatTaken = Counter.builder("reservations_declined_total")
				.tag("reason", "seat_taken").register(registry);
		this.declinedPerUserLimit = Counter.builder("reservations_declined_total")
				.tag("reason", "per_user_limit").register(registry);
		this.declinedDifferentBody = Counter.builder("reservations_declined_total")
				.tag("reason", "different_body").register(registry);
	}

	public void recordHeld() {
		held.increment();
	}

	public void recordConfirmed() {
		confirmed.increment();
	}

	public void recordIdempotentReplay() {
		idempotentReplay.increment();
	}

	public void recordDecline(ErrorCode code) {
		switch (code) {
			case SEAT_TAKEN -> declinedSeatTaken.increment();
			case PER_USER_LIMIT -> declinedPerUserLimit.increment();
			case IDEMPOTENCY_DIFFERENT_BODY -> declinedDifferentBody.increment();
			default -> {
			}
		}
	}

	public void updateSeatsAvailable(UUID showId, int count) {
		seatsAvailable.computeIfAbsent(showId, id -> {
			AtomicInteger holder = new AtomicInteger(count);
			Gauge.builder("seats_available", holder, AtomicInteger::get)
					.tag("show_id", id.toString())
					.register(registry);
			return holder;
		}).set(count);
	}
}
