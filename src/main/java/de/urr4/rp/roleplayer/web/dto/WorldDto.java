package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.World;

import java.time.Instant;

public record WorldDto(String id, String name, String slug, Instant createdAt) {
    public static WorldDto from(World world) {
        return new WorldDto(world.id(), world.name(), world.slug(), world.createdAt());
    }
}
