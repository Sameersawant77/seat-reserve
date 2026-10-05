package com.seat.reserve.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.seat.reserve.domain.ShowRecord;

@Repository
public class ShowRepository {

	private static final RowMapper<ShowRecord> MAPPER = (rs, rowNum) -> new ShowRecord(
			rs.getObject("id", UUID.class),
			rs.getString("name"),
			rs.getLong("price_paise"),
			rs.getInt("per_user_limit"),
			rs.getInt("hold_ttl_seconds"));

	private final JdbcTemplate jdbc;

	public ShowRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void insert(ShowRecord show) {
		jdbc.update(
				"INSERT INTO shows (id, name, price_paise, per_user_limit, hold_ttl_seconds) VALUES (?,?,?,?,?)",
				show.id(), show.name(), show.pricePaise(), show.perUserLimit(), show.holdTtlSeconds());
	}

	public Optional<ShowRecord> findById(UUID id) {
		List<ShowRecord> rows = jdbc.query("SELECT * FROM shows WHERE id = ?", MAPPER, id);
		return rows.stream().findFirst();
	}
}
