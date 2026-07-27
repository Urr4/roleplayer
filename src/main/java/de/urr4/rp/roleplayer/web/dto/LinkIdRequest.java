package de.urr4.rp.roleplayer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkIdRequest(@NotBlank String id) {
}
