package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Adventure(
        String id,
        String chronicleId,
        String name,
        AdventureStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        WorldExtractionStatus worldExtractionStatus,
        String worldExtractionError,
        String draftFactsText) {

    public boolean isActive() {
        return status == AdventureStatus.ACTIVE;
    }
}
