package de.urr4.rp.roleplayer.web.dto;

import jakarta.validation.constraints.NotNull;

public record PushWorldFactsRequest(@NotNull String factsText) {
}
