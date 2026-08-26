package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Chronicle;

import java.time.Instant;

public record ChronicleDto(String id, String name, Instant createdAt) {
    public static ChronicleDto from(Chronicle chronicle) {
        return new ChronicleDto(chronicle.id(), chronicle.name(), chronicle.createdAt());
    }
}
