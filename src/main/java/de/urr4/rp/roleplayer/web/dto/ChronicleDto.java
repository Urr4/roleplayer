package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Chronicle;

import java.time.Instant;

public record ChronicleDto(String id, String name, Instant createdAt, String worldId, String worldName, String worldSlug) {
    public static ChronicleDto from(Chronicle chronicle, String worldName, String worldSlug) {
        return new ChronicleDto(chronicle.id(), chronicle.name(), chronicle.createdAt(), chronicle.worldId(), worldName, worldSlug);
    }
}
