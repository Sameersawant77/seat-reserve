package com.seat.reserve.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<Map<String, Object>> handleApi(ApiException ex) {
		HttpStatus status = mapStatus(ex.code());
		return ResponseEntity.status(status).body(Map.of(
				"error", ex.code().name(),
				"message", ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		return ResponseEntity.badRequest().body(Map.of(
				"error", ErrorCode.BAD_REQUEST.name(),
				"message", "Validation failed"));
	}

	private static HttpStatus mapStatus(ErrorCode code) {
		return switch (code) {
			case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
			case FORBIDDEN -> HttpStatus.FORBIDDEN;
			case NOT_FOUND -> HttpStatus.NOT_FOUND;
			case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
			case SEAT_TAKEN, PER_USER_LIMIT, IDEMPOTENCY_DIFFERENT_BODY, IDEMPOTENCY_IN_PROGRESS, INVALID_STATE ->
				HttpStatus.CONFLICT;
		};
	}
}
