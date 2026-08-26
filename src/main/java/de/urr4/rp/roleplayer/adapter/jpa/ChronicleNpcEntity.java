package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "chronicle_npcs", uniqueConstraints = @UniqueConstraint(columnNames = {"chronicle_id", "npc_id"}))
public class ChronicleNpcEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "chronicle_id")
    private String chronicleId;
    @Column(name = "npc_id")
    private String npcId;
    protected ChronicleNpcEntity() {}
    public ChronicleNpcEntity(String chronicleId, String npcId) { this.chronicleId = chronicleId; this.npcId = npcId; }
    public Long getId() { return id; }
    public String getChronicleId() { return chronicleId; }
    public String getNpcId() { return npcId; }
}
