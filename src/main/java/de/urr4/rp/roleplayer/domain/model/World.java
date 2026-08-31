package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record World(String id, String name, String slug, Instant createdAt) {
}
