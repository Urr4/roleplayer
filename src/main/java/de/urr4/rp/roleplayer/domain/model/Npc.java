package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

/**
 * An NPC described by a short KI-generated character sheet instead of the
 * previous randomly-rolled motive/status/mood attributes.
 */
public record Npc(String id, String name, String firstImpression, String goal, String attitude,
                  String rulesAndTaboos, String quirks, String originChronicleId, Instant createdAt) {
}
