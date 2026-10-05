package com.seat.reserve.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.seat.reserve.domain.ReservationRecord;
import com.seat.reserve.domain.ReservationStatus;

@Repository
public class ReservationRepository {

	private static final RowMapper<ReservationRecord> MAPPER = (rs, rowNum) -> new ReservationRecord(
			rs.getObject("id", UUID.class),
			rs.getObject("show_id", UUID.class),
			rs.getString("user_id"),
			ReservationStatus.valueOf(rs.getString("status").toUpperCase()),
			rs.getLong("amount_paise"));

	private final JdbcTemplate jdbc;

	public ReservationRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(ReservationRecord reservation) {
		jdbc.update(
				"INSERT INTO reservations (id, show_id, user_id, status, amount_paise) VALUES (?,?,?,?,?)",
				reservation.id(), reservation.showId(), reservation.userId(),
				reservation.status().name().toLowerCase(), reservation.amountPaise());
	}

	public Optional<ReservationRecord> findById(UUID id) {
		List<ReservationRecord> rows = jdbc.query("SELECT * FROM reservations WHERE id = ?", MAPPER, id);
		return rows.stream().findFirst();
	}

	public int updateStatus(UUID id, ReservationStatus status) {
		return jdbc.update("UPDATE reservations SET status = ? WHERE id = ?", status.name().toLowerCase(), id);
	}

	public int markExpiredBatch(List<UUID> ids) {
		if (ids.isEmpty()) {
			return 0;
		}
		String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
		return jdbc.update(
				"UPDATE reservations SET status = 'expired' WHERE id IN (" + placeholders + ") AND status = 'held'",
				ids.toArray());
	}
}
