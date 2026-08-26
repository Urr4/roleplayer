package de.urr4.rp.roleplayer.domain.port.out;

import java.util.List;

public interface SessionNpcLinkRepository {
    /**
     * Links an existing (global) NPC into a session — used both for saving a
     * freshly created NPC into its session and for importing an NPC that
     * originated in a different chronicle.
     */
    void link(String chronicleId, String npcId);

    void unlink(String chronicleId, String npcId);

    List<String> findNpcIdsByChronicle(String chronicleId);
}
