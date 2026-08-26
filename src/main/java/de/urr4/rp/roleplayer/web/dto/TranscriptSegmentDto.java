package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;

import java.time.Instant;

public record TranscriptSegmentDto(String id, String recordingId, String speakerLabel, long startMs, long endMs,
                                   String text, Instant createdAt) {
    public static TranscriptSegmentDto from(TranscriptSegment segment) {
        return new TranscriptSegmentDto(segment.id(), segment.recordingId(), segment.speakerLabel(), segment.startMs(),
                segment.endMs(), segment.text(), segment.createdAt());
    }
}
