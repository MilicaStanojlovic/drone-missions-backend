package com.project.drone_missions.web.dto.bid;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record BidRequest(
        @NotNull @Positive BigDecimal amount,
        @Size(max = 500) String message
) {
}
