package com.seat.reserve.repo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.seat.reserve.domain.SeatRecord;
import com.seat.reserve.domain.SeatStatus;

@Repository
public class SeatRepository {

	private static final RowMapper<SeatRecord> MAPPER = (rs, rowNum) -> new SeatRecord(
			rs.getObject("id", UUID.class),
			rs.getObject("show_id", UUID.class),
			rs.getString("label"),
			SeatStatus.valueOf(rs.getString("status").toUpperCase()),
			rs.getObject("reservation_id", UUID.class),
			rs.getString("held_by"),
			rs.getTimestamp("hold_expires_at") != null ? rs.getTimestamp("hold_expires_at").toInstant() : null);

	private final JdbcTemplate jdbc;

	public SeatRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insertBatch(UUID showId, List<String> labels) {
		for (String label : labels) {
			jdbc.update(
					"INSERT INTO seats (id, show_id, label, status) VALUES (?,?,?,?)",
					UUID.randomUUID(), showId, label, SeatStatus.AVAILABLE.name().toLowerCase());
		}
	}

	public List<SeatRecord> findByShowId(UUID showId) {
		return jdbc.query("SELECT * FROM seats WHERE show_id = ? ORDER BY label", MAPPER, showId);
	}

	public void acquireUserShowLock(UUID showId, String userId) {
		jdbc.execute("SELECT pg_advisory_xact_lock(?)", (org.springframework.jdbc.core.PreparedStatementCallback<Void>) ps -> {
			ps.setLong(1, advisoryKey(showId, userId));
			ps.execute();
			return null;
		});
	}

	private static long advisoryKey(UUID showId, String userId) {
		return ((long) showId.hashCode() << 32) ^ (userId.hashCode() & 0xffffffffL);
	}

	public List<SeatRecord> lockSeatsForUpdate(UUID showId, List<String> labels) {
		if (labels.isEmpty()) {
			return List.of();
		}
		List<String> sorted = new ArrayList<>(labels);
		sorted.sort(Comparator.naturalOrder());
		String placeholders = String.join(",", sorted.stream().map(l -> "?").toList());
		List<Object> args = new ArrayList<>();
		args.add(showId);
		args.addAll(sorted);
		return jdbc.query(
				"SELECT * FROM seats WHERE show_id = ? AND label IN (" + placeholders + ") ORDER BY label FOR UPDATE",
				MAPPER,
				args.toArray());
	}

	public int countActiveSeatsForUser(UUID showId, String userId, Instant now) {
		Integer count = jdbc.queryForObject(
				"""
				SELECT COUNT(*) FROM seats s
				JOIN reservations r ON r.id = s.reservation_id
				WHERE s.show_id = ?
				  AND r.user_id = ?
				  AND r.status IN ('held', 'confirmed')
				  AND (s.status = 'confirmed'
				       OR (s.status = 'held' AND (s.hold_expires_at IS NULL OR s.hold_expires_at > ?)))
				""",
				Integer.class,
				showId, userId, java.sql.Timestamp.from(now));
		return count != null ? count : 0;
	}

	public int markHeld(UUID seatId, UUID reservationId, String userId, Instant expiresAt, Instant now) {
		return jdbc.update(
				"""
				UPDATE seats SET status = 'held', reservation_id = ?, held_by = ?, hold_expires_at = ?
				WHERE id = ? AND (status = 'available'
				  OR (status = 'held' AND hold_expires_at IS NOT NULL AND hold_expires_at <= ?))
				""",
				reservationId, userId, java.sql.Timestamp.from(expiresAt), seatId,
				java.sql.Timestamp.from(now));
	}

	public int expireHeldSeats(Instant now) {
		return jdbc.update(
				"""
				UPDATE seats SET status = 'available', reservation_id = NULL, held_by = NULL, hold_expires_at = NULL
				WHERE status = 'held' AND hold_expires_at IS NOT NULL AND hold_expires_at <= ?
				""",
				java.sql.Timestamp.from(now));
	}

	public List<UUID> findHeldReservationsWithoutHeldSeats() {
		return jdbc.queryForList(
				"""
				SELECT r.id FROM reservations r
				WHERE r.status = 'held'
				  AND NOT EXISTS (
				    SELECT 1 FROM seats s
				    WHERE s.reservation_id = r.id AND s.status = 'held'
				  )
				""",
				UUID.class);
	}

	public List<String> labelsForReservation(UUID reservationId) {
		return jdbc.queryForList(
				"SELECT label FROM seats WHERE reservation_id = ? ORDER BY label",
				String.class,
				reservationId);
	}

	public int confirmSeats(UUID reservationId, String userId) {
		return jdbc.update(
				"""
				UPDATE seats SET status = 'confirmed'
				WHERE reservation_id = ? AND held_by = ? AND status = 'held'
				  AND hold_expires_at IS NOT NULL AND hold_expires_at > NOW()
				""",
				reservationId, userId);
	}

	public int releaseSeats(UUID reservationId, String userId) {
		return jdbc.update(
				"""
				UPDATE seats SET status = 'available', reservation_id = NULL, held_by = NULL, hold_expires_at = NULL
				WHERE reservation_id = ? AND held_by = ? AND status = 'held'
				""",
				reservationId, userId);
	}

	public int countByEffectiveStatus(UUID showId, SeatStatus effective, Instant now) {
		String sql = switch (effective) {
			case AVAILABLE -> """
				SELECT COUNT(*) FROM seats WHERE show_id = ?
				  AND (status = 'available'
				    OR (status = 'held' AND hold_expires_at IS NOT NULL AND hold_expires_at <= ?))
				""";
			case HELD -> """
				SELECT COUNT(*) FROM seats WHERE show_id = ?
				  AND status = 'held' AND hold_expires_at IS NOT NULL AND hold_expires_at > ?
				""";
			case CONFIRMED -> """
				SELECT COUNT(*) FROM seats WHERE show_id = ? AND status = 'confirmed'
				""";
		};
		Object[] args = effective == SeatStatus.CONFIRMED
				? new Object[] { showId }
				: new Object[] { showId, java.sql.Timestamp.from(now) };
		Integer count = jdbc.queryForObject(sql, Integer.class, args);
		return count != null ? count : 0;
	}
}
