package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Recording;

import java.time.Instant;

public record RecordingDto(String id, String chronicleId, String adventureId, String source, String status, Instant startedAt,
                           Instant endedAt, String audioObjectKey, String transcriptObjectKey, String audioUrl) {
    public static RecordingDto from(Recording recording, String audioUrl) {
        return new RecordingDto(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source().name(),
                recording.status().name(), recording.startedAt(), recording.endedAt(), recording.audioObjectKey(),
                recording.transcriptObjectKey(), audioUrl);
    }
}
