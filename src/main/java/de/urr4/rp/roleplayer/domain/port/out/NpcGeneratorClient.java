package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.NpcGeneration;

public interface NpcGeneratorClient {
    /**
     * Generates a short NPC character sheet (first impression, goal,
     * attitude, rules/taboos, quirks) from a brief free-text description
     * such as "friendly merchant".
     */
    NpcGeneration generate(String shortDescription);
}
