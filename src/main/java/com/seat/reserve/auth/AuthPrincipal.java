package com.seat.reserve.auth;

public record AuthPrincipal(String userId, Role role) {
	public boolean isAdmin() {
		return role == Role.ADMIN;
	}
}
