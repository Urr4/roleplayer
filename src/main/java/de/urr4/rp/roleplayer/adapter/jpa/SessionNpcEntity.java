package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "session_npcs", uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "npc_id"}))
public class SessionNpcEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "npc_id")
    private String npcId;

    protected SessionNpcEntity() {
    }

    public SessionNpcEntity(String sessionId, String npcId) {
        this.sessionId = sessionId;
        this.npcId = npcId;
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getNpcId() {
        return npcId;
    }
}
