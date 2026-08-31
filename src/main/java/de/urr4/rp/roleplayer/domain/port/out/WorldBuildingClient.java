package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.VaultNoteChange;

import java.util.List;

public interface WorldBuildingClient {
    boolean isReachable();

    List<VaultNoteChange> extractFacts(String worldName, String worldSlug, String chronicleName, String adventureName,
                                       String transcriptText, List<String> existingNoteSummaries);
}
