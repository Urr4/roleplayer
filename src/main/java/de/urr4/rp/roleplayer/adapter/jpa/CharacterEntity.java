package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "characters")
public class CharacterEntity {

    @Id
    private String id;
    private String chronicleId;
    private String name;
    private String playerId;
    private String pdfObjectKey;
    private Instant createdAt;

    protected CharacterEntity() {}

    public CharacterEntity(String id, String chronicleId, String name, String playerId, String pdfObjectKey, Instant createdAt) {
        this.id = id; this.chronicleId = chronicleId; this.name = name; this.playerId = playerId; this.pdfObjectKey = pdfObjectKey; this.createdAt = createdAt;
    }
    public String getId() { return id; }
    public String getChronicleId() { return chronicleId; }
    public String getName() { return name; }
    public String getPlayerId() { return playerId; }
    public String getPdfObjectKey() { return pdfObjectKey; }
    public Instant getCreatedAt() { return createdAt; }
}
