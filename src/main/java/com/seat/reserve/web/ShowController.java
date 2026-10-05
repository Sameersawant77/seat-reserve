package com.seat.reserve.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.seat.reserve.auth.AuthContext;
import com.seat.reserve.auth.AuthPrincipal;
import com.seat.reserve.api.ApiException;
import com.seat.reserve.api.ErrorCode;
import com.seat.reserve.service.ShowService;
import com.seat.reserve.web.dto.CreateShowRequest;
import com.seat.reserve.web.dto.ShowResponse;

import jakarta.validation.Valid;

@RestController
public class ShowController {

	private final ShowService showService;

	public ShowController(ShowService showService) {
		this.showService = showService;
	}

	@PostMapping("/shows")
	@ResponseStatus(HttpStatus.CREATED)
	public ShowResponse createShow(@Valid @RequestBody CreateShowRequest request) {
		requireAdmin();
		return showService.createShow(request);
	}

	@GetMapping("/shows/{id}")
	public ShowResponse getShow(@PathVariable UUID id) {
		return showService.getShow(id);
	}

	private static void requireAdmin() {
		AuthPrincipal principal = AuthContext.require();
		if (!principal.isAdmin()) {
			throw new ApiException(ErrorCode.FORBIDDEN, "Admin role required");
		}
	}
}
