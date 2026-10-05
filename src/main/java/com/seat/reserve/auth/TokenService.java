package com.seat.reserve.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.seat.reserve.config.ReserveProperties;

@Service
public class TokenService {

	private static final String HMAC_ALGO = "HmacSHA256";

	private final byte[] secret;

	public TokenService(ReserveProperties properties) {
		this.secret = properties.auth().hmacSecret().getBytes(StandardCharsets.UTF_8);
	}

	public String mint(String userId, Role role, long ttlSeconds) {
		long exp = Instant.now().getEpochSecond() + ttlSeconds;
		String payload = userId + "|" + role.name() + "|" + exp;
		String sig = sign(payload);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
				+ "." + sig;
	}

	public AuthPrincipal parse(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		String[] parts = token.split("\\.", 2);
		if (parts.length != 2) {
			return null;
		}
		String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
		if (!sign(payload).equals(parts[1])) {
			return null;
		}
		String[] fields = payload.split("\\|", 3);
		if (fields.length != 3) {
			return null;
		}
		long exp = Long.parseLong(fields[2]);
		if (Instant.now().getEpochSecond() > exp) {
			return null;
		}
		return new AuthPrincipal(fields[0], Role.valueOf(fields[1]));
	}

	private String sign(String payload) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGO);
			mac.init(new SecretKeySpec(secret, HMAC_ALGO));
			return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HMAC unavailable", e);
		}
	}

	public static String hashRequest(String canonical) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
