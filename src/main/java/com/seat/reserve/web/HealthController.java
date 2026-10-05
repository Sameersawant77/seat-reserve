package com.seat.reserve.web;

import java.util.Map;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	private final JdbcTemplate jdbc;
	private final ApplicationAvailability availability;

	public HealthController(JdbcTemplate jdbc, ApplicationAvailability availability) {
		this.jdbc = jdbc;
		this.availability = availability;
	}

	@GetMapping("/healthz")
	public Map<String, String> liveness() {
		return Map.of("status", "UP");
	}

	@GetMapping("/readyz")
	public ResponseEntity<Map<String, String>> readiness() {
		if (availability.getLivenessState() != LivenessState.CORRECT) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(Map.of("status", "DOWN", "reason", "liveness"));
		}
		if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(Map.of("status", "DOWN", "reason", "readiness"));
		}
		try {
			jdbc.queryForObject("SELECT 1", Integer.class);
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(Map.of("status", "DOWN", "reason", "database"));
		}
		return ResponseEntity.ok(Map.of("status", "UP"));
	}
}
