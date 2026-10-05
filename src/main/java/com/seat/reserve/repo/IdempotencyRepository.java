package com.seat.reserve.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.seat.reserve.domain.IdempotencyRecord;

@Repository
public class IdempotencyRepository {

	private static final RowMapper<IdempotencyRecord> MAPPER = (rs, rowNum) -> new IdempotencyRecord(
			rs.getString("user_id"),
			rs.getString("key"),
			rs.getString("request_hash"),
			rs.getObject("reservation_id", UUID.class),
			rs.getString("status"));

	private final JdbcTemplate jdbc;

	public IdempotencyRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public boolean tryInsertInProgress(String userId, String key, String requestHash) {
		int inserted = jdbc.update(
				"""
				INSERT INTO idempotency_keys (user_id, key, request_hash, status)
				VALUES (?, ?, ?, 'in_progress')
				ON CONFLICT (user_id, key) DO NOTHING
				""",
				userId, key, requestHash);
		return inserted == 1;
	}

	public Optional<IdempotencyRecord> find(String userId, String key) {
		List<IdempotencyRecord> rows = jdbc.query(
				"SELECT * FROM idempotency_keys WHERE user_id = ? AND key = ?",
				MAPPER,
				userId, key);
		return rows.stream().findFirst();
	}

	public void complete(String userId, String key, UUID reservationId) {
		jdbc.update(
				"UPDATE idempotency_keys SET status = 'completed', reservation_id = ? WHERE user_id = ? AND key = ?",
				reservationId, userId, key);
	}

	public void deleteInProgress(String userId, String key) {
		jdbc.update(
				"DELETE FROM idempotency_keys WHERE user_id = ? AND key = ? AND status = 'in_progress'",
				userId, key);
	}
}
