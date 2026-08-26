package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "adventure_characters")
public class AdventureCharacterEntity {
    @Id
    private String id;
    private String adventureId;
    private String characterId;
    private Instant addedAt;

    protected AdventureCharacterEntity() {}

    public AdventureCharacterEntity(String id, String adventureId, String characterId, Instant addedAt) {
        this.id = id;
        this.adventureId = adventureId;
        this.characterId = characterId;
        this.addedAt = addedAt;
    }

    public String getId() { return id; }
    public String getAdventureId() { return adventureId; }
    public String getCharacterId() { return characterId; }
    public Instant getAddedAt() { return addedAt; }
}
