package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Npc(String id, String name, String motive, NpcStatus status, String mood, String originSessionId,
                   Instant createdAt) {
}
