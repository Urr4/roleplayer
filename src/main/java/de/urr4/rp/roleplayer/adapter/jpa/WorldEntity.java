package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "worlds")
public class WorldEntity {

    @Id
    private String id;
    private String name;
    private String slug;
    private Instant createdAt;

    protected WorldEntity() {
    }

    public WorldEntity(String id, String name, String slug, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Instant getCreatedAt() { return createdAt; }
}
