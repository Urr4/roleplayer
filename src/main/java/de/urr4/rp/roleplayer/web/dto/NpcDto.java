package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Npc;
import de.urr4.rp.roleplayer.domain.model.NpcStatus;

import java.time.Instant;

public record NpcDto(String id, String name, String motive, NpcStatus status, String mood, String originChronicleId,
                      Instant createdAt) {
    public static NpcDto from(Npc npc) {
        return new NpcDto(npc.id(), npc.name(), npc.motive(), npc.status(), npc.mood(), npc.originChronicleId(),
                npc.createdAt());
    }
}
