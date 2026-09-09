package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.AdventureStatus;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldFactExtractionServiceTest {

    @Test
    void gatherWorldFactsResolvesRecordinglessAdventureAdventuresEvenWhenOllamaIsUnreachable() {
        AdventureRepository adventureRepository = mock(AdventureRepository.class);
        ChronicleRepository chronicleRepository = mock(ChronicleRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        WorldBuildingClient worldBuildingClient = mock(WorldBuildingClient.class);
        VaultRepository vaultRepository = mock(VaultRepository.class);
        RecordingService recordingService = mock(RecordingService.class);

        String adventureId = "adv-1";
        String chronicleId = "chr-1";
        String worldId = "world-1";
        String recordingId = "rec-1";

        Adventure adventure = new Adventure(adventureId, chronicleId, "Session 1", AdventureStatus.COMPLETED,
                Instant.now(), Instant.now(), Instant.now(), WorldExtractionStatus.NONE, null, null);
        Recording recording = new Recording(recordingId, chronicleId, adventureId, RecordingSource.MICROPHONE,
                RecordingStatus.AWAITING_ASR, Instant.now(), Instant.now(), "audio.webm", null);

        when(adventureRepository.findById(adventureId)).thenReturn(Optional.of(adventure));
        when(chronicleRepository.findById(chronicleId))
                .thenReturn(Optional.of(new Chronicle(chronicleId, "Chronicle", Instant.now(), worldId)));
        when(worldRepository.findById(worldId))
                .thenReturn(Optional.of(new World(worldId, "World", "world", Instant.now())));
        // A recording exists (so the button is enabled), but it hasn't been
        // transcribed yet (e.g. still AWAITING_ASR) - there's simply no
        // transcript text to summarize yet, independent of whether Ollama is
        // reachable at all.
        when(recordingService.listRecordings(adventureId)).thenReturn(List.of(recording));
        when(recordingService.getAdventureTranscript(adventureId)).thenReturn(List.of());
        when(adventureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(worldBuildingClient.isReachable()).thenReturn(false);

        WorldFactExtractionService service = new WorldFactExtractionService(adventureRepository, chronicleRepository,
                worldRepository, worldBuildingClient, vaultRepository, recordingService);

        Adventure result = service.gatherWorldFacts(adventureId);

        assertThat(result.worldExtractionStatus()).isEqualTo(WorldExtractionStatus.PENDING);
        verify(worldBuildingClient, never()).summarizeFacts(any(), any(), any(), any(), any());
    }

    @Test
    void gatherWorldFactsThrowsWhenNoRecordingsExist() {
        AdventureRepository adventureRepository = mock(AdventureRepository.class);
        ChronicleRepository chronicleRepository = mock(ChronicleRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        WorldBuildingClient worldBuildingClient = mock(WorldBuildingClient.class);
        VaultRepository vaultRepository = mock(VaultRepository.class);
        RecordingService recordingService = mock(RecordingService.class);

        String adventureId = "adv-1";
        Adventure adventure = new Adventure(adventureId, "chr-1", "Session 1", AdventureStatus.ACTIVE,
                Instant.now(), Instant.now(), null, WorldExtractionStatus.NONE, null, null);

        when(adventureRepository.findById(adventureId)).thenReturn(Optional.of(adventure));
        when(recordingService.listRecordings(adventureId)).thenReturn(List.of());

        WorldFactExtractionService service = new WorldFactExtractionService(adventureRepository, chronicleRepository,
                worldRepository, worldBuildingClient, vaultRepository, recordingService);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.gatherWorldFacts(adventureId));
    }

    @Test
    void gatherWorldFactsCallsOllamaDirectlyWithoutAReachabilityPreflightAndWorksWhileAdventureIsStillActive() {
        AdventureRepository adventureRepository = mock(AdventureRepository.class);
        ChronicleRepository chronicleRepository = mock(ChronicleRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        WorldBuildingClient worldBuildingClient = mock(WorldBuildingClient.class);
        VaultRepository vaultRepository = mock(VaultRepository.class);
        RecordingService recordingService = mock(RecordingService.class);

        String adventureId = "adv-1";
        String chronicleId = "chr-1";
        String worldId = "world-1";
        String recordingId = "rec-1";

        // Gathering must be usable at any time, not just once the adventure
        // has ended - here the adventure is still ACTIVE.
        Adventure adventure = new Adventure(adventureId, chronicleId, "Session 1", AdventureStatus.ACTIVE,
                Instant.now(), Instant.now(), null, WorldExtractionStatus.NONE, null, null);
        Recording recording = new Recording(recordingId, chronicleId, adventureId, RecordingSource.MICROPHONE,
                RecordingStatus.DONE, Instant.now(), Instant.now(), "audio.webm", "transcript.json");
        TranscriptSegment segment = new TranscriptSegment("seg-1", recordingId, "Spieler 1", 0L, 1000L,
                "Wir betreten die Taverne.", Instant.now());

        when(adventureRepository.findById(adventureId)).thenReturn(Optional.of(adventure));
        when(chronicleRepository.findById(chronicleId))
                .thenReturn(Optional.of(new Chronicle(chronicleId, "Chronicle", Instant.now(), worldId)));
        when(worldRepository.findById(worldId))
                .thenReturn(Optional.of(new World(worldId, "World", "world", Instant.now())));
        when(recordingService.listRecordings(adventureId)).thenReturn(List.of(recording));
        when(recordingService.getAdventureTranscript(adventureId)).thenReturn(List.of(segment));
        when(adventureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(worldBuildingClient.summarizeFacts(any(), any(), any(), any(), any())).thenReturn("Die Gruppe betrat eine Taverne.");

        WorldFactExtractionService service = new WorldFactExtractionService(adventureRepository, chronicleRepository,
                worldRepository, worldBuildingClient, vaultRepository, recordingService);

        Adventure result = service.gatherWorldFacts(adventureId);

        // No preflight reachability probe - the real endpoint is called
        // directly, exactly like the mealplaner backend does.
        verify(worldBuildingClient, never()).isReachable();
        verify(worldBuildingClient).summarizeFacts(any(), any(), any(), any(), any());

        assertThat(result.worldExtractionStatus()).isEqualTo(WorldExtractionStatus.DRAFT_READY);
        assertThat(result.draftFactsText()).isEqualTo("Die Gruppe betrat eine Taverne.");
    }

    @Test
    void pushFactsToVaultStripsRedundantWorldSlugPrefixAndEnforcesMdExtension() {
        AdventureRepository adventureRepository = mock(AdventureRepository.class);
        ChronicleRepository chronicleRepository = mock(ChronicleRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        WorldBuildingClient worldBuildingClient = mock(WorldBuildingClient.class);
        VaultRepository vaultRepository = mock(VaultRepository.class);
        RecordingService recordingService = mock(RecordingService.class);

        String adventureId = "adv-1";
        String chronicleId = "chr-1";
        String worldId = "world-1";
        String worldSlug = "test-welt";

        Adventure adventure = new Adventure(adventureId, chronicleId, "Session 1", AdventureStatus.COMPLETED,
                Instant.now(), Instant.now(), Instant.now(), WorldExtractionStatus.DRAFT_READY, null, "Fakten...");

        when(adventureRepository.findById(adventureId)).thenReturn(Optional.of(adventure));
        when(chronicleRepository.findById(chronicleId))
                .thenReturn(Optional.of(new Chronicle(chronicleId, "Chronicle", Instant.now(), worldId)));
        when(worldRepository.findById(worldId))
                .thenReturn(Optional.of(new World(worldId, "Testwelt", worldSlug, Instant.now())));
        when(vaultRepository.listNotes(any())).thenReturn(List.of());
        when(adventureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // The model was told not to repeat the world slug and to always add
        // ".md", but reproduces the exact malformed output observed live:
        // a path prefixed with the world slug again, and no ".md" suffix.
        when(worldBuildingClient.mergeFactsIntoVault(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new VaultNoteChange(worldSlug + "/Geografie", "Geografie", "# Geografie", true)));

        WorldFactExtractionService service = new WorldFactExtractionService(adventureRepository, chronicleRepository,
                worldRepository, worldBuildingClient, vaultRepository, recordingService);

        Adventure result = service.pushFactsToVault(adventureId, "Fakten...");

        ArgumentCaptor<List<VaultFileWrite>> writesCaptor = ArgumentCaptor.forClass(List.class);
        verify(vaultRepository).commitChanges(any(), writesCaptor.capture());
        List<VaultFileWrite> writes = writesCaptor.getValue();

        assertThat(writes).hasSize(1);
        // Must NOT be "content/worlds/test-welt/test-welt/Geografie" (the
        // duplicated-folder bug) and must end in ".md" (Quartz/Obsidian only
        // render ".md" files - anything else is served as an opaque static
        // asset and never shows up on the site).
        assertThat(writes.get(0).path()).isEqualTo("content/worlds/test-welt/Geografie.md");
        assertThat(result.worldExtractionStatus()).isEqualTo(WorldExtractionStatus.DONE);
    }

    @Test
    void pushFactsToVaultStripsWorldsAndContentPrefixVariantsRegardlessOfOrderOrCombination() {
        AdventureRepository adventureRepository = mock(AdventureRepository.class);
        ChronicleRepository chronicleRepository = mock(ChronicleRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        WorldBuildingClient worldBuildingClient = mock(WorldBuildingClient.class);
        VaultRepository vaultRepository = mock(VaultRepository.class);
        RecordingService recordingService = mock(RecordingService.class);

        String adventureId = "adv-1";
        String chronicleId = "chr-1";
        String worldId = "world-1";
        String worldSlug = "test-welt";

        Adventure adventure = new Adventure(adventureId, chronicleId, "Session 1", AdventureStatus.COMPLETED,
                Instant.now(), Instant.now(), Instant.now(), WorldExtractionStatus.DRAFT_READY, null, "Fakten...");

        when(adventureRepository.findById(adventureId)).thenReturn(Optional.of(adventure));
        when(chronicleRepository.findById(chronicleId))
                .thenReturn(Optional.of(new Chronicle(chronicleId, "Chronicle", Instant.now(), worldId)));
        when(worldRepository.findById(worldId))
                .thenReturn(Optional.of(new World(worldId, "Testwelt", worldSlug, Instant.now())));
        when(vaultRepository.listNotes(any())).thenReturn(List.of());
        when(adventureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Reproduces the live "worlds/test-welt.md" case, plus a couple of
        // other prefix variants the model has been observed to invent -
        // none of these mention "content/worlds/{slug}/" verbatim, but all
        // mirror some part of the vault's own folder structure.
        when(worldBuildingClient.mergeFactsIntoVault(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        new VaultNoteChange("worlds/" + worldSlug + ".md", "Testwelt", "# Testwelt", true),
                        new VaultNoteChange("worlds/" + worldSlug + "/Locations/Paspaturia.md", "Paspaturia", "# Paspaturia", true),
                        new VaultNoteChange("Locations/Dorf.md", "Dorf", "# Dorf", true)
                ));

        WorldFactExtractionService service = new WorldFactExtractionService(adventureRepository, chronicleRepository,
                worldRepository, worldBuildingClient, vaultRepository, recordingService);

        service.pushFactsToVault(adventureId, "Fakten...");

        ArgumentCaptor<List<VaultFileWrite>> writesCaptor = ArgumentCaptor.forClass(List.class);
        verify(vaultRepository).commitChanges(any(), writesCaptor.capture());
        List<String> paths = writesCaptor.getValue().stream().map(VaultFileWrite::path).toList();

        assertThat(paths).containsExactlyInAnyOrder(
                "content/worlds/test-welt/test-welt.md",
                "content/worlds/test-welt/Locations/Paspaturia.md",
                "content/worlds/test-welt/Locations/Dorf.md");
    }
}
