package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Session;

import java.time.Instant;

public record SessionDto(String id, String name, Instant createdAt) {
    public static SessionDto from(Session session) {
        return new SessionDto(session.id(), session.name(), session.createdAt());
    }
}
