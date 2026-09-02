package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.VaultNoteChange;

import java.util.List;

public interface WorldBuildingClient {
    boolean isReachable();

    /**
     * Phase 1: summarize the raw adventure transcript into a single, plain
     * free-text digest of the facts recognized about characters, NPCs, the
     * world, culture, politics, etc. This text is shown to the user for
     * review/correction before anything is written to the vault.
     */
    String summarizeFacts(String worldName, String worldSlug, String chronicleName, String adventureName,
                          String transcriptText);

    /**
     * Phase 2: turn the (possibly user-edited) facts text into Markdown notes
     * and merge them with the existing vault notes for the world. Only
     * called once the user explicitly triggers "Add facts to world".
     */
    List<VaultNoteChange> mergeFactsIntoVault(String worldName, String worldSlug, String chronicleName, String adventureName,
                                              String factsText, List<String> existingNoteSummaries);
}
