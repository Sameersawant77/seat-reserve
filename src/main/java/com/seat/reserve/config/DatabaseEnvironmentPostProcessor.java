package com.seat.reserve.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

	private static final String SOURCE = "reserveDatabaseUrl";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		if (environment.acceptsProfiles(Profiles.of("test"))) {
			return;
		}
		Map<String, Object> overrides = new HashMap<>();
		String databaseUrl = environment.getProperty("DATABASE_URL");
		if (databaseUrl != null && !databaseUrl.isBlank()) {
			applyPostgresUrl(overrides, databaseUrl);
		} else {
			String springUrl = environment.getProperty("SPRING_DATASOURCE_URL");
			if (springUrl != null && isPostgresScheme(springUrl)) {
				applyPostgresUrl(overrides, springUrl);
			}
		}
		if (!overrides.isEmpty()) {
			environment.getPropertySources().addFirst(new MapPropertySource(SOURCE, overrides));
		}
	}

	private static void applyPostgresUrl(Map<String, Object> overrides, String databaseUrl) {
		if (databaseUrl.startsWith("jdbc:")) {
			overrides.put("spring.datasource.url", databaseUrl);
			return;
		}
		PostgresUrl parsed = PostgresUrl.parse(databaseUrl);
		overrides.put("spring.datasource.url", parsed.jdbcUrl());
		if (!parsed.username().isEmpty()) {
			overrides.put("spring.datasource.username", parsed.username());
			overrides.put("spring.datasource.password", parsed.password());
		}
	}

	private static boolean isPostgresScheme(String url) {
		return url.startsWith("postgres://") || url.startsWith("postgresql://");
	}
}
