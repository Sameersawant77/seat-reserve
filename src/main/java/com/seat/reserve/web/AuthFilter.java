package com.seat.reserve.web;

import java.io.IOException;
import java.util.Set;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.seat.reserve.auth.AuthContext;
import com.seat.reserve.auth.AuthPrincipal;
import com.seat.reserve.auth.TokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(2)
public class AuthFilter extends OncePerRequestFilter {

	private static final Set<String> PUBLIC = Set.of(
			"/healthz",
			"/readyz",
			"/actuator/prometheus",
			"/actuator/health",
			"/auth/token");

	private final TokenService tokenService;

	public AuthFilter(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String path = request.getRequestURI();
		if (isPublic(path) || isPublicShowRead(request.getMethod(), path)) {
			filterChain.doFilter(request, response);
			return;
		}
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		AuthPrincipal principal = tokenService.parse(header.substring(7).trim());
		if (principal == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}
		AuthContext.set(principal);
		try {
			filterChain.doFilter(request, response);
		} finally {
			AuthContext.clear();
		}
	}

	private static boolean isPublic(String path) {
		if (PUBLIC.contains(path)) {
			return true;
		}
		return path.startsWith("/actuator/health");
	}

	private static boolean isPublicShowRead(String method, String path) {
		return "GET".equalsIgnoreCase(method) && path.startsWith("/shows/") && !path.endsWith("/reserve");
	}
}
