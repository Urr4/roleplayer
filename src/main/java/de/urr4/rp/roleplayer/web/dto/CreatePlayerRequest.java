package de.urr4.rp.roleplayer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePlayerRequest(@NotBlank String name) {
}
