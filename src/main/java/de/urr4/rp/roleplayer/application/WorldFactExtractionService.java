package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.model.VaultFileWrite;
import de.urr4.rp.roleplayer.domain.model.VaultNoteChange;
import de.urr4.rp.roleplayer.domain.model.World;
import de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import de.urr4.rp.roleplayer.domain.port.out.VaultRepository;
import de.urr4.rp.roleplayer.domain.port.out.WorldBuildingClient;
import de.urr4.rp.roleplayer.domain.port.out.WorldRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class WorldFactExtractionService {
    private static final Logger log = LoggerFactory.getLogger(WorldFactExtractionService.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final AdventureRepository adventureRepository;
    private final ChronicleRepository chronicleRepository;
    private final WorldRepository worldRepository;
    private final WorldBuildingClient worldBuildingClient;
    private final VaultRepository vaultRepository;
    private final RecordingService recordingService;

    public WorldFactExtractionService(AdventureRepository adventureRepository, ChronicleRepository chronicleRepository,
                                      WorldRepository worldRepository, WorldBuildingClient worldBuildingClient,
                                      VaultRepository vaultRepository, RecordingService recordingService) {
        this.adventureRepository = adventureRepository;
        this.chronicleRepository = chronicleRepository;
        this.worldRepository = worldRepository;
        this.worldBuildingClient = worldBuildingClient;
        this.vaultRepository = vaultRepository;
        this.recordingService = recordingService;
    }

    // ── Phase 1: gather facts from the transcript into a plain-text draft ──

    @Async("recordingTaskExecutor")
    public void onAdventureStopped(Adventure adventure) {
        gatherDraft(adventure, true);
    }

    /**
     * Retries phase 1 (gathering) for adventures still waiting on
     * transcription or Ollama. Phase 2 (the vault push) is never retried
     * automatically - it only runs when the user clicks "Add facts to world".
     */
    public void retryPending() {
        List<Adventure> candidates = adventureRepository.findAll().stream()
                .filter(a -> a.worldExtractionStatus() == WorldExtractionStatus.PENDING)
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        // Don't bail out for every candidate just because Ollama happens to
        // be unreachable right now: gatherDraft() can resolve some of them
        // (no recordings at all, or a transcript that only just appeared)
        // without ever calling Ollama. Its own reachability check further
        // down still skips the actual LLM call per-candidate when needed.
        for (Adventure adventure : candidates) {
            gatherDraft(adventure, false);
        }
    }

    private void gatherDraft(Adventure adventure, boolean setPendingBeforeReachabilityCheck) {
        Optional<Chronicle> chronicleOptional = chronicleRepository.findById(adventure.chronicleId());
        if (chronicleOptional.isEmpty() || chronicleOptional.get().worldId() == null) {
            log.info("Skipping world-fact gathering for adventure {} because no world is linked", adventure.id());
            return;
        }
        Chronicle chronicle = chronicleOptional.get();
        Optional<World> worldOptional = worldRepository.findById(chronicle.worldId());
        if (worldOptional.isEmpty()) {
            log.info("Skipping world-fact gathering for adventure {} because world {} is missing", adventure.id(), chronicle.worldId());
            return;
        }
        if (recordingService.listRecordings(adventure.id()).isEmpty()) {
            // No recordings at all: nothing to summarize automatically. Show
            // an empty, editable draft right away so the user can type notes
            // by hand and push them via "Add facts to world".
            log.info("Adventure {} has no recordings; presenting an empty facts draft for manual notes", adventure.id());
            saveDraft(adventure, WorldExtractionStatus.DRAFT_READY, null, "");
            return;
        }
        String transcriptText = recordingService.getAdventureTranscript(adventure.id()).stream()
                .map(this::formatSegment)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        if (transcriptText.isBlank()) {
            // The adventure has recordings, but no transcript segments exist
            // yet - most likely because the final live-recording flush's ASR
            // call runs asynchronously (see RecordingProcessingService) and
            // can still be in flight when the adventure is marked stopped, or
            // a just-uploaded recording is still waiting on WhisperX/queued
            // for retry. This is transient, not permanent: mark PENDING so
            // retryPending() keeps retrying every couple of minutes once
            // transcript segments actually show up.
            log.info("Transcript not ready yet for adventure {}; marking world-fact gathering pending for retry", adventure.id());
            saveDraft(adventure, WorldExtractionStatus.PENDING, null, adventure.draftFactsText());
            return;
        }
        Adventure pending = adventure;
        if (setPendingBeforeReachabilityCheck || adventure.worldExtractionStatus() != WorldExtractionStatus.PENDING) {
            pending = saveDraft(adventure, WorldExtractionStatus.PENDING, null, adventure.draftFactsText());
        }
        if (!worldBuildingClient.isReachable()) {
            return;
        }
        try {
            String factsText = worldBuildingClient.summarizeFacts(worldOptional.get().name(), worldOptional.get().slug(),
                    chronicle.name(), pending.name(), transcriptText);
            saveDraft(pending, WorldExtractionStatus.DRAFT_READY, null, factsText);
        } catch (Exception e) {
            log.error("World-fact gathering failed for adventure {}", pending.id(), e);
            saveDraft(pending, WorldExtractionStatus.PENDING, truncate(e.getMessage()), pending.draftFactsText());
        }
    }

    // ── Phase 2: merge the (user-reviewed) draft text into the vault ──────

    /**
     * Explicitly triggered by "Add facts to world". Saves the given text as
     * the new draft, then asks the LLM to turn it into Markdown and merges it
     * into the Obsidian vault. On failure the draft text is preserved and the
     * status becomes FAILED so the user can correct/retry - no automatic
     * background retry for this phase.
     */
    public Adventure pushFactsToVault(String adventureId, String factsText) {
        Adventure adventure = adventureRepository.findById(adventureId)
                .orElseThrow(() -> new NoSuchElementException("Adventure not found: " + adventureId));
        Chronicle chronicle = chronicleRepository.findById(adventure.chronicleId())
                .orElseThrow(() -> new IllegalStateException("Chronicle not found for adventure " + adventureId));
        if (chronicle.worldId() == null) {
            throw new IllegalStateException("No world linked to chronicle " + chronicle.id());
        }
        World world = worldRepository.findById(chronicle.worldId())
                .orElseThrow(() -> new IllegalStateException("World not found: " + chronicle.worldId()));

        Adventure pushing = saveDraft(adventure, WorldExtractionStatus.PUSHING, null, factsText);
        try {
            String worldFolderPath = "content/worlds/" + world.slug();
            List<String> noteSummaries = vaultRepository.listNotes(worldFolderPath).stream()
                    .map(summary -> summary.path() + "\n" + summary.excerpt())
                    .toList();
            List<VaultNoteChange> changes = worldBuildingClient.mergeFactsIntoVault(world.name(), world.slug(), chronicle.name(),
                    pushing.name(), factsText, noteSummaries);
            List<VaultFileWrite> writes = changes.stream()
                    .map(change -> toWrite(world.slug(), change))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            vaultRepository.commitChanges("World facts from adventure '" + pushing.name() + "'", writes);
            return saveDraft(pushing, WorldExtractionStatus.DONE, null, factsText);
        } catch (Exception e) {
            log.error("World-fact vault push failed for adventure {}", adventureId, e);
            return saveDraft(pushing, WorldExtractionStatus.FAILED, truncate(e.getMessage()), factsText);
        }
    }

    private VaultFileWrite toWrite(String worldSlug, VaultNoteChange change) {
        String relativePath = change.relativePath();
        if (relativePath == null || relativePath.isBlank()) return null;
        String sanitized = relativePath.replace('\\', '/');
        if (sanitized.startsWith("/") || sanitized.contains("..")) {
            return null;
        }
        String fullPath = "content/worlds/" + worldSlug + "/" + sanitized;
        return new VaultFileWrite(fullPath, change.markdownContent());
    }

    private Adventure saveDraft(Adventure adventure, WorldExtractionStatus status, String error, String draftFactsText) {
        Adventure updated = new Adventure(adventure.id(), adventure.chronicleId(), adventure.name(), adventure.status(),
                adventure.createdAt(), adventure.startedAt(), adventure.endedAt(), status, error, draftFactsText);
        return adventureRepository.save(updated);
    }

    private String truncate(String message) {
        if (message == null) return null;
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    private String formatSegment(TranscriptSegment segment) {
        return segment.speakerLabel() + ": " + segment.text();
    }
}
