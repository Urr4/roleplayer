package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.AdventureStatus;
import de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "adventures")
public class AdventureEntity {
    @Id
    private String id;
    private String chronicleId;
    private String name;
    @Enumerated(EnumType.STRING)
    private AdventureStatus status;
    private Instant createdAt;
    private Instant startedAt;
    private Instant endedAt;
    @Enumerated(EnumType.STRING)
    private WorldExtractionStatus worldExtractionStatus;
    private String worldExtractionError;

    protected AdventureEntity() {}

    public AdventureEntity(String id, String chronicleId, String name, AdventureStatus status,
                            Instant createdAt, Instant startedAt, Instant endedAt,
                            WorldExtractionStatus worldExtractionStatus, String worldExtractionError) {
        this.id = id;
        this.chronicleId = chronicleId;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.worldExtractionStatus = worldExtractionStatus;
        this.worldExtractionError = worldExtractionError;
    }
    public String getId() { return id; }
    public String getChronicleId() { return chronicleId; }
    public String getName() { return name; }
    public AdventureStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public WorldExtractionStatus getWorldExtractionStatus() { return worldExtractionStatus; }
    public String getWorldExtractionError() { return worldExtractionError; }
}
