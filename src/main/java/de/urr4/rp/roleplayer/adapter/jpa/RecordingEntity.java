package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "recordings")
public class RecordingEntity {
    @Id private String id;
    private String chronicleId;
    private String adventureId;
    @Enumerated(EnumType.STRING) private RecordingSource source;
    @Enumerated(EnumType.STRING) private RecordingStatus status;
    private Instant startedAt;
    private Instant endedAt;
    private String audioObjectKey;
    private String transcriptObjectKey;
    protected RecordingEntity() {}
    public RecordingEntity(String id, String chronicleId, String adventureId, RecordingSource source, RecordingStatus status, Instant startedAt, Instant endedAt, String audioObjectKey, String transcriptObjectKey) {
        this.id=id; this.chronicleId=chronicleId; this.adventureId=adventureId; this.source=source; this.status=status; this.startedAt=startedAt; this.endedAt=endedAt; this.audioObjectKey=audioObjectKey; this.transcriptObjectKey=transcriptObjectKey;
    }
    public String getId() { return id; }
    public String getChronicleId() { return chronicleId; }
    public String getAdventureId() { return adventureId; }
    public RecordingSource getSource() { return source; }
    public RecordingStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getAudioObjectKey() { return audioObjectKey; }
    public String getTranscriptObjectKey() { return transcriptObjectKey; }
}
