package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Recording(String id, String chronicleId, String adventureId, RecordingSource source, RecordingStatus status, Instant startedAt,
                        Instant endedAt, String audioObjectKey, String transcriptObjectKey) {
}
