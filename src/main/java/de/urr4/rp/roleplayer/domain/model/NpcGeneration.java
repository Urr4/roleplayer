package de.urr4.rp.roleplayer.domain.model;

/**
 * The KI-generated NPC character sheet produced from a short user-supplied
 * description (e.g. "friendly merchant").
 */
public record NpcGeneration(String firstImpression, String goal, String attitude, String rulesAndTaboos,
                            String quirks) {
}
