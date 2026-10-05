package com.seat.reserve.config;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class DataSourceConfiguration {

	@Bean
	@Primary
	public DataSource dataSource(Environment env) {
		String url = env.getProperty("SPRING_DATASOURCE_URL", env.getProperty("spring.datasource.url"));
		String username = env.getProperty("SPRING_DATASOURCE_USERNAME", env.getProperty("spring.datasource.username"));
		String password = env.getProperty("SPRING_DATASOURCE_PASSWORD", env.getProperty("spring.datasource.password"));
		String databaseUrl = env.getProperty("DATABASE_URL");
		if (databaseUrl != null && !databaseUrl.isBlank()) {
			PostgresUrl parsed = PostgresUrl.parse(databaseUrl);
			url = parsed.jdbcUrl();
			username = parsed.username();
			password = parsed.password();
		} else if (url != null && (url.startsWith("postgres://") || url.startsWith("postgresql://"))) {
			PostgresUrl parsed = PostgresUrl.parse(url);
			url = parsed.jdbcUrl();
			if (username == null || username.isBlank()) {
				username = parsed.username();
			}
			if (password == null) {
				password = parsed.password();
			}
		}
		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(url);
		ds.setUsername(username);
		ds.setPassword(password);
		ds.setMaximumPoolSize(Integer.parseInt(env.getProperty("DB_POOL_SIZE", "20")));
		return ds;
	}
}
