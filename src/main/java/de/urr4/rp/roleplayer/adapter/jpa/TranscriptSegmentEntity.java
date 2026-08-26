package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "transcript_segments")
public class TranscriptSegmentEntity {

    @Id
    private String id;

    private String recordingId;

    private String speakerLabel;

    private long startMs;

    private long endMs;

    private String text;

    private Instant createdAt;

    protected TranscriptSegmentEntity() {
    }

    public TranscriptSegmentEntity(String id, String recordingId, String speakerLabel, long startMs, long endMs,
                                   String text, Instant createdAt) {
        this.id = id;
        this.recordingId = recordingId;
        this.speakerLabel = speakerLabel;
        this.startMs = startMs;
        this.endMs = endMs;
        this.text = text;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRecordingId() {
        return recordingId;
    }

    public String getSpeakerLabel() {
        return speakerLabel;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
