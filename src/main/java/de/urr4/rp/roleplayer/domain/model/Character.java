package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Character(String id, String name, String playerId, String pdfObjectKey, Instant createdAt) {
}
