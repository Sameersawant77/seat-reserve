package com.seat.reserve.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.seat.reserve.auth.TokenService;
import com.seat.reserve.web.dto.TokenRequest;
import com.seat.reserve.web.dto.TokenResponse;

import jakarta.validation.Valid;

@RestController
public class AuthController {

	private final TokenService tokenService;

	public AuthController(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	@PostMapping("/auth/token")
	public TokenResponse mint(@Valid @RequestBody TokenRequest request) {
		long ttl = request.ttlSeconds() > 0 ? request.ttlSeconds() : 3600;
		return new TokenResponse(tokenService.mint(request.userId(), request.role(), ttl));
	}
}
