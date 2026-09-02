package de.urr4.rp.roleplayer.domain.model;

public enum WorldExtractionStatus {
    /** No world-fact extraction has been started for this adventure yet. */
    NONE,
    /** Phase 1 (gathering facts from the transcript) is waiting on transcription or the LLM. */
    PENDING,
    /** Phase 1 finished (or there were no recordings): draftFactsText is ready to be reviewed/edited. */
    DRAFT_READY,
    /** Phase 2 (merging the reviewed text into the vault as Markdown) is in progress. */
    PUSHING,
    /** Phase 2 succeeded: the facts have been pushed to the Obsidian vault. */
    DONE,
    /** Phase 2 failed; the edited draft text is preserved so the user can retry. */
    FAILED
}
