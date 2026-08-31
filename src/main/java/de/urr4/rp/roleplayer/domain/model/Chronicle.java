package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Chronicle(String id, String name, Instant createdAt, String worldId) {
}
