package com.seat.reserve.auth;

public final class AuthContext {

	private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

	private AuthContext() {
	}

	public static void set(AuthPrincipal principal) {
		CURRENT.set(principal);
	}

	public static AuthPrincipal get() {
		return CURRENT.get();
	}

	public static AuthPrincipal require() {
		AuthPrincipal principal = CURRENT.get();
		if (principal == null) {
			throw new com.seat.reserve.api.ApiException(
					com.seat.reserve.api.ErrorCode.UNAUTHORIZED, "Missing or invalid authentication");
		}
		return principal;
	}

	public static void clear() {
		CURRENT.remove();
	}
}
