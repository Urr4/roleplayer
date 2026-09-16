package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "npcs")
public class NpcEntity {

    @Id
    private String id;

    private String name;

    @Lob
    @Column(name = "first_impression")
    private String firstImpression;

    @Lob
    private String goal;

    @Lob
    private String attitude;

    @Lob
    @Column(name = "rules_and_taboos")
    private String rulesAndTaboos;

    @Lob
    private String quirks;

    private String originChronicleId;

    private Instant createdAt;

    protected NpcEntity() {
    }

    public NpcEntity(String id, String name, String firstImpression, String goal, String attitude,
                      String rulesAndTaboos, String quirks, String originChronicleId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.firstImpression = firstImpression;
        this.goal = goal;
        this.attitude = attitude;
        this.rulesAndTaboos = rulesAndTaboos;
        this.quirks = quirks;
        this.originChronicleId = originChronicleId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFirstImpression() {
        return firstImpression;
    }

    public String getGoal() {
        return goal;
    }

    public String getAttitude() {
        return attitude;
    }

    public String getRulesAndTaboos() {
        return rulesAndTaboos;
    }

    public String getQuirks() {
        return quirks;
    }

    public String getOriginChronicleId() {
        return originChronicleId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
