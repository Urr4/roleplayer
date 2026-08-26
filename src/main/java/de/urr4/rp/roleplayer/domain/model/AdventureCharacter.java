package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

/**
 * Marks that a character participates in an adventure. No player reference —
 * players are tied to characters at the (chronicle-scoped) Character level;
 * if a player's character changes, a new Character is created and added here
 * instead.
 */
public record AdventureCharacter(String id, String adventureId, String characterId, Instant addedAt) {
}
