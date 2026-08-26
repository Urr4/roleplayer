package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chronicles")
public class ChronicleEntity {

    @Id
    private String id;
    private String name;
    private Instant createdAt;

    protected ChronicleEntity() {
    }

    public ChronicleEntity(String id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
