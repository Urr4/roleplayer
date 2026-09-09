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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    /**
     * Manually triggers phase 1 (gathering) for a single adventure - used by
     * the "Gather World-Facts" button, which the frontend only enables once
     * at least one recording/transcript exists and Ollama is reachable. This
     * is never invoked automatically (there used to be an automatic trigger
     * on adventure stop plus a background retry scheduler for adventures
     * stuck PENDING; both were removed since a hung/unreachable Ollama made
     * the "Waiting on facts" UI spin forever with no way for the user to
     * intervene). The user decides when to (re-)run gathering, regardless of
     * whether the adventure has ended or its current world-fact status.
     */
    public Adventure gatherWorldFacts(String adventureId) {
        Adventure adventure = adventureRepository.findById(adventureId)
                .orElseThrow(() -> new NoSuchElementException("Adventure not found: " + adventureId));
        if (recordingService.listRecordings(adventureId).isEmpty()) {
            throw new IllegalStateException("No recordings available to gather world facts from");
        }
        return gatherDraft(adventure, true);
    }

    private Adventure gatherDraft(Adventure adventure, boolean forcePendingResave) {
        Optional<Chronicle> chronicleOptional = chronicleRepository.findById(adventure.chronicleId());
        if (chronicleOptional.isEmpty() || chronicleOptional.get().worldId() == null) {
            log.info("Skipping world-fact gathering for adventure {} because no world is linked", adventure.id());
            return adventure;
        }
        Chronicle chronicle = chronicleOptional.get();
        Optional<World> worldOptional = worldRepository.findById(chronicle.worldId());
        if (worldOptional.isEmpty()) {
            log.info("Skipping world-fact gathering for adventure {} because world {} is missing", adventure.id(), chronicle.worldId());
            return adventure;
        }
        if (recordingService.listRecordings(adventure.id()).isEmpty()) {
            // No recordings at all: nothing to summarize automatically. Show
            // an empty, editable draft right away so the user can type notes
            // by hand and push them via "Add facts to world".
            log.info("Adventure {} has no recordings; presenting an empty facts draft for manual notes", adventure.id());
            return saveDraft(adventure, WorldExtractionStatus.DRAFT_READY, null, "");
        }
        String transcriptText = recordingService.getAdventureTranscript(adventure.id()).stream()
                .map(this::formatSegment)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        if (transcriptText.isBlank()) {
            // The adventure has recordings, but no transcript segments exist
            // yet - most likely because transcription is still in progress /
            // queued for retry (e.g. WhisperX was unreachable). This is
            // transient, not permanent: mark PENDING and let the user click
            // "Gather World-Facts" again once transcription has completed.
            log.info("Transcript not ready yet for adventure {}; marking world-fact gathering pending", adventure.id());
            return saveDraft(adventure, WorldExtractionStatus.PENDING, null, adventure.draftFactsText());
        }
        Adventure pending = adventure;
        if (forcePendingResave || adventure.worldExtractionStatus() != WorldExtractionStatus.PENDING) {
            pending = saveDraft(adventure, WorldExtractionStatus.PENDING, null, adventure.draftFactsText());
        }
        // Call Ollama directly instead of gating on a separate isReachable()
        // preflight (a GET /api/tags with its own short timeout): that probe
        // is an extra point of failure independent of the actual /api/generate
        // call, and a transient hiccup or a slow response (e.g. Ollama busy
        // loading/running another request) there previously aborted this
        // whole attempt *silently* - without ever contacting Ollama for the
        // real request - even though the actual call, with its much more
        // generous READ_TIMEOUT, would likely have succeeded. This mirrors
        // how the mealplaner backend calls Ollama: no preflight check, just
        // try the real endpoint and let the timeout/catch below handle it.
        try {
            log.info("Calling Ollama to gather world facts for adventure {} ({} chars of transcript)",
                    pending.id(), transcriptText.length());
            String factsText = worldBuildingClient.summarizeFacts(worldOptional.get().name(), worldOptional.get().slug(),
                    chronicle.name(), pending.name(), transcriptText);
            return saveDraft(pending, WorldExtractionStatus.DRAFT_READY, null, factsText);
        } catch (Exception e) {
            log.error("World-fact gathering failed for adventure {}", pending.id(), e);
            return saveDraft(pending, WorldExtractionStatus.PENDING, truncate(e.getMessage()), pending.draftFactsText());
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
        // The LLM is instructed not to repeat any part of the vault's own
        // folder structure in "path" (the world folder is added here), but
        // weaker models keep reinventing variations of it anyway - e.g.
        // "{worldSlug}/Note.md", "content/worlds/{worldSlug}/Note.md", or
        // just "worlds/Note.md"/"worlds/{worldSlug}/Note.md". Rather than
        // enumerating every combination, repeatedly strip a leading segment
        // whenever it matches "content", "worlds", or the world slug, so
        // whatever prefix variant the model invents collapses to the same
        // clean relative path and never nests the world folder inside
        // itself (e.g. content/worlds/x/x/Note.md or .../x/worlds/Note.md).
        List<String> segments = new ArrayList<>(List.of(sanitized.split("/")));
        while (segments.size() > 1 && (segments.get(0).equalsIgnoreCase("content")
                || segments.get(0).equalsIgnoreCase("worlds")
                || segments.get(0).equalsIgnoreCase(worldSlug))) {
            segments.remove(0);
        }
        sanitized = String.join("/", segments);
        if (sanitized.isBlank()) return null;
        // Quartz (and Obsidian) only treat ".md" files as note content -
        // anything else is served as an opaque static asset and never
        // rendered/linked, silently making the pushed note invisible on the
        // site. The model is instructed to include the extension, but
        // enforce it defensively in case it forgets.
        if (!sanitized.toLowerCase(java.util.Locale.ROOT).endsWith(".md")) {
            sanitized = sanitized + ".md";
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
