package com.seat.reserve.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reserve")
public record ReserveProperties(
		Auth auth,
		Hold hold
) {
	public record Auth(String hmacSecret, String adminUserId) {
	}

	public record Hold(int defaultTtlSeconds, int defaultPerUserLimit) {
	}
}
