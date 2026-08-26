package de.urr4.rp.roleplayer.domain.port.out;

import java.util.List;

public interface ChronicleNpcLinkRepository {
    void link(String chronicleId, String npcId);

    void unlink(String chronicleId, String npcId);

    List<String> findNpcIdsByChronicle(String chronicleId);
}
