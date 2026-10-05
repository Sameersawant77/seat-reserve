package com.seat.reserve.web.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateShowRequest(
		@NotBlank String name,
		@NotEmpty List<@NotBlank String> seats,
		@NotNull @Min(0) Long pricePaise) {
}
