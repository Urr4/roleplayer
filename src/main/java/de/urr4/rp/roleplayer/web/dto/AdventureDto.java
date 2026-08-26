package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.AdventureStatus;

import java.time.Instant;

public record AdventureDto(String id, String chronicleId, String name, AdventureStatus status,
                           Instant createdAt, Instant startedAt, Instant endedAt) {
    public static AdventureDto from(Adventure adventure) {
        return new AdventureDto(adventure.id(), adventure.chronicleId(), adventure.name(), adventure.status(),
                adventure.createdAt(), adventure.startedAt(), adventure.endedAt());
    }
}
