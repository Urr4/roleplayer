package de.urr4.rp.roleplayer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNpcRequest(@NotBlank String name, @NotBlank String firstImpression, @NotBlank String goal,
                                @NotBlank String attitude, @NotBlank String rulesAndTaboos, @NotBlank String quirks) {
}
