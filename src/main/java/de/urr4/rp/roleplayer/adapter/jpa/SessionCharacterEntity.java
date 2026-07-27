package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "session_characters", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "character_id"}))
public class SessionCharacterEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "character_id")
    private String characterId;

    protected SessionCharacterEntity() {
    }

    public SessionCharacterEntity(String sessionId, String characterId) {
        this.sessionId = sessionId;
        this.characterId = characterId;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCharacterId() {
        return characterId;
    }
}
