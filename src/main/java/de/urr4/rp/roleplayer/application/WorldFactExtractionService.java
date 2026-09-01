package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.model.VaultFileSummary;
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

import java.time.Instant;
import java.util.List;
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

    @Async("recordingTaskExecutor")
    public void onAdventureStopped(Adventure adventure) {
        processAdventure(adventure, true);
    }

    public void retryPending() {
        List<Adventure> candidates = adventureRepository.findAll().stream()
                .filter(a -> a.worldExtractionStatus() == WorldExtractionStatus.PENDING || a.worldExtractionStatus() == WorldExtractionStatus.FAILED)
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        if (!worldBuildingClient.isReachable()) {
            log.debug("Ollama still unreachable; {} adventure(s) remain pending for world extraction", candidates.size());
            return;
        }
        for (Adventure adventure : candidates) {
            processAdventure(adventure, false);
        }
    }

    private void processAdventure(Adventure adventure, boolean setPendingBeforeReachabilityCheck) {
        Optional<Chronicle> chronicleOptional = chronicleRepository.findById(adventure.chronicleId());
        if (chronicleOptional.isEmpty() || chronicleOptional.get().worldId() == null) {
            log.info("Skipping world extraction for adventure {} because no world is linked", adventure.id());
            return;
        }
        Chronicle chronicle = chronicleOptional.get();
        Optional<World> worldOptional = worldRepository.findById(chronicle.worldId());
        if (worldOptional.isEmpty()) {
            log.info("Skipping world extraction for adventure {} because world {} is missing", adventure.id(), chronicle.worldId());
            return;
        }
        if (recordingService.listRecordings(adventure.id()).isEmpty()) {
            log.info("Skipping world extraction for adventure {} because it has no recordings", adventure.id());
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
            // WorldFactRetryScheduler keeps retrying every couple of minutes
            // once transcript segments actually show up, instead of silently
            // and permanently skipping extraction (and the Obsidian push
            // that depends on it) for good.
            log.info("Transcript not ready yet for adventure {}; marking world extraction pending for retry", adventure.id());
            saveStatus(adventure, WorldExtractionStatus.PENDING, null);
            return;
        }
        Adventure pending = adventure;
        if (setPendingBeforeReachabilityCheck || adventure.worldExtractionStatus() != WorldExtractionStatus.PENDING) {
            pending = saveStatus(adventure, WorldExtractionStatus.PENDING, null);
        }
        if (!worldBuildingClient.isReachable()) {
            return;
        }
        runExtraction(pending, chronicle, worldOptional.get(), transcriptText);
    }

    private void runExtraction(Adventure adventure, Chronicle chronicle, World world, String transcriptText) {
        try {
            String worldFolderPath = "content/worlds/" + world.slug();
            List<String> noteSummaries = vaultRepository.listNotes(worldFolderPath).stream()
                    .map(summary -> summary.path() + "\n" + summary.excerpt())
                    .toList();
            List<VaultNoteChange> changes = worldBuildingClient.extractFacts(world.name(), world.slug(), chronicle.name(),
                    adventure.name(), transcriptText, noteSummaries);
            List<VaultFileWrite> writes = changes.stream()
                    .map(change -> toWrite(world.slug(), change))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            vaultRepository.commitChanges("World facts from adventure '" + adventure.name() + "'", writes);
            saveStatus(adventure, WorldExtractionStatus.DONE, null);
        } catch (Exception e) {
            log.error("World extraction failed for adventure {}", adventure.id(), e);
            saveStatus(adventure, WorldExtractionStatus.FAILED, truncate(e.getMessage()));
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

    private Adventure saveStatus(Adventure adventure, WorldExtractionStatus status, String error) {
        Adventure updated = new Adventure(adventure.id(), adventure.chronicleId(), adventure.name(), adventure.status(),
                adventure.createdAt(), adventure.startedAt(), adventure.endedAt(), status, error);
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
