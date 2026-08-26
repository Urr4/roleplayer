package de.urr4.rp.roleplayer.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateAdventureRequest(@NotBlank String name, List<String> characterIds) {
}
