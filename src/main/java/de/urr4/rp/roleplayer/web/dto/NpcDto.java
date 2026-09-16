package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Npc;

import java.time.Instant;

public record NpcDto(String id, String name, String firstImpression, String goal, String attitude,
                      String rulesAndTaboos, String quirks, String originChronicleId, Instant createdAt) {
    public static NpcDto from(Npc npc) {
        return new NpcDto(npc.id(), npc.name(), npc.firstImpression(), npc.goal(), npc.attitude(),
                npc.rulesAndTaboos(), npc.quirks(), npc.originChronicleId(), npc.createdAt());
    }
}
