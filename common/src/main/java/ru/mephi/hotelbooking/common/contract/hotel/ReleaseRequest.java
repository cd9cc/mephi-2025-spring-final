package ru.mephi.hotelbooking.common.contract.hotel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReleaseRequest(
        @NotBlank @Size(max = 64) String requestId,
        @NotNull Long bookingId
) {
}
