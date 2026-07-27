package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.NpcStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateNpcRequest(@NotBlank String name, @NotBlank String motive, @NotNull NpcStatus status,
                                @NotBlank String mood) {
}
