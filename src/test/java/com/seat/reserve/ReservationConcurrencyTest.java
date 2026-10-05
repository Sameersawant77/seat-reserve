package com.seat.reserve;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seat.reserve.support.IntegrationTestBase;

class ReservationConcurrencyTest extends IntegrationTestBase {

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private ObjectMapper objectMapper;

	private String adminToken;
	private UUID showId;

	@BeforeEach
	void setUp() throws Exception {
		adminToken = mintToken("admin", "ADMIN");
		List<String> seats = new ArrayList<>();
		for (char row = 'A'; row <= 'E'; row++) {
			for (int i = 1; i <= 10; i++) {
				seats.add(row + i);
			}
		}
		HttpHeaders headers = authHeaders(adminToken);
		Map<String, Object> body = Map.of(
				"name", "test-show",
				"seats", seats,
				"price_paise", 25000);
		ResponseEntity<String> created = rest.exchange(
				"/shows", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
		assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		showId = UUID.fromString(objectMapper.readTree(created.getBody()).get("id").asText());
	}

	@Test
	void hotSeatStormExactlyOneWinner() throws Exception {
		int threads = 200;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			String user = "storm-" + i;
			String token = mintToken(user, "USER");
			tasks.add(() -> reserveStatus(token, "A1", "key-" + user));
		}
		List<Future<Integer>> futures = pool.invokeAll(tasks);
		pool.shutdown();
		int created = 0;
		int conflict = 0;
		int serverError = 0;
		for (Future<Integer> future : futures) {
			int code = future.get();
			if (code == 201) {
				created++;
			} else if (code == 409) {
				conflict++;
			} else if (code >= 500) {
				serverError++;
			}
		}
		assertThat(serverError).isZero();
		assertThat(created).isEqualTo(1);
		assertThat(conflict).isEqualTo(threads - 1);
		assertShowReconciled();
	}

	@Test
	void idempotencyReplayAndDifferentBody() throws Exception {
		String token = mintToken("idem-user", "USER");
		assertThat(reserveStatus(token, "B1", "same-key")).isEqualTo(201);
		assertThat(reserveStatus(token, "B1", "same-key")).isEqualTo(201);
		int differentBody = reserveStatus(token, "B2", "same-key");
		assertThat(differentBody).isEqualTo(409);
	}

	@Test
	void perUserLimitUnderParallelReserves() throws Exception {
		String token = mintToken("limit-user", "USER");
		ExecutorService pool = Executors.newFixedThreadPool(10);
		List<Callable<Integer>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			String seat = "C" + (i + 1);
			tasks.add(() -> reserveStatus(token, seat, "limit-" + seat));
		}
		List<Future<Integer>> futures = pool.invokeAll(tasks);
		pool.shutdown();
		int success = 0;
		for (Future<Integer> future : futures) {
			if (future.get() == 201) {
				success++;
			}
		}
		assertThat(success).isLessThanOrEqualTo(4);
		assertShowReconciled();
	}

	private int reserveStatus(String token, String seat, String idempotencyKey) {
		HttpHeaders headers = authHeaders(token);
		headers.set("Idempotency-Key", idempotencyKey);
		Map<String, Object> body = Map.of(
				"seats", List.of(seat),
				"idempotency_key", idempotencyKey);
		ResponseEntity<String> response = rest.exchange(
				"/shows/" + showId + "/reserve",
				HttpMethod.POST,
				new HttpEntity<>(body, headers),
				String.class);
		return response.getStatusCode().value();
	}

	private void assertShowReconciled() throws Exception {
		ResponseEntity<String> show = rest.getForEntity("/shows/" + showId, String.class);
		JsonNode node = objectMapper.readTree(show.getBody());
		JsonNode counts = node.get("counts");
		int sum = counts.get("available").asInt()
				+ counts.get("held").asInt()
				+ counts.get("confirmed").asInt();
		assertThat(sum).isEqualTo(counts.get("total").asInt());
		assertThat(node.get("reconciled").asBoolean()).isTrue();
	}

	private String mintToken(String userId, String role) throws Exception {
		Map<String, Object> body = Map.of(
				"user_id", userId,
				"role", role,
				"ttl_seconds", 3600);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> response = rest.exchange(
				"/auth/token", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
		return objectMapper.readTree(response.getBody()).get("token").asText();
	}

	private static HttpHeaders authHeaders(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
