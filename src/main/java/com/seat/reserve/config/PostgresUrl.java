package com.seat.reserve.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

record PostgresUrl(String jdbcUrl, String username, String password) {

	static PostgresUrl parse(String databaseUrl) {
		String normalized = databaseUrl.replace("postgres://", "postgresql://");
		if (normalized.startsWith("jdbc:")) {
			return new PostgresUrl(normalized, "", "");
		}
		URI uri = URI.create(normalized);
		String userInfo = uri.getUserInfo();
		String username = "";
		String password = "";
		if (userInfo != null) {
			int colon = userInfo.indexOf(':');
			if (colon >= 0) {
				username = decode(userInfo.substring(0, colon));
				password = decode(userInfo.substring(colon + 1));
			} else {
				username = decode(userInfo);
			}
		}
		String jdbcUrl = "jdbc:postgresql://" + uri.getHost()
				+ (uri.getPort() > 0 ? ":" + uri.getPort() : "")
				+ uri.getPath()
				+ (uri.getQuery() != null ? "?" + uri.getQuery() : "");
		return new PostgresUrl(jdbcUrl, username, password);
	}

	private static String decode(String value) {
		return URLDecoder.decode(value, StandardCharsets.UTF_8);
	}
}
