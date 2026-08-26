package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.NpcStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "npcs")
public class NpcEntity {

    @Id
    private String id;

    private String name;

    private String motive;

    @Enumerated(EnumType.STRING)
    private NpcStatus status;

    private String mood;

    private String originChronicleId;

    private Instant createdAt;

    protected NpcEntity() {
    }

    public NpcEntity(String id, String name, String motive, NpcStatus status, String mood, String originChronicleId,
                      Instant createdAt) {
        this.id = id;
        this.name = name;
        this.motive = motive;
        this.status = status;
        this.mood = mood;
        this.originChronicleId = originChronicleId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getMotive() {
        return motive;
    }

    public NpcStatus getStatus() {
        return status;
    }

    public String getMood() {
        return mood;
    }

    public String getOriginChronicleId() {
        return originChronicleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
