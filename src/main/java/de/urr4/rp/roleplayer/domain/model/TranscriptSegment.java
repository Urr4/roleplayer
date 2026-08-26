package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record TranscriptSegment(String id, String recordingId, String speakerLabel, long startMs, long endMs,
                                String text, Instant createdAt) {
}
