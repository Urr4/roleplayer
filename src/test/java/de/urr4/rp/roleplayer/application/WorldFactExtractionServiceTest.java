package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.AdventureStatus;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
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
    void retryPendingResolvesRecordinglessAdventuresEvenWhenOllamaIsUnreachable() {
        AdventureRepository adventureRepository = mock(AdventureRepository.class);
        ChronicleRepository chronicleRepository = mock(ChronicleRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        WorldBuildingClient worldBuildingClient = mock(WorldBuildingClient.class);
        VaultRepository vaultRepository = mock(VaultRepository.class);
        RecordingService recordingService = mock(RecordingService.class);

        String adventureId = "adv-1";
        String chronicleId = "chr-1";
        String worldId = "world-1";

        Adventure pendingAdventure = new Adventure(adventureId, chronicleId, "Session 1", AdventureStatus.COMPLETED,
                Instant.now(), Instant.now(), Instant.now(), WorldExtractionStatus.PENDING, null, null);

        when(adventureRepository.findAll()).thenReturn(List.of(pendingAdventure));
        when(chronicleRepository.findById(chronicleId))
                .thenReturn(Optional.of(new Chronicle(chronicleId, "Chronicle", Instant.now(), worldId)));
        when(worldRepository.findById(worldId))
                .thenReturn(Optional.of(new World(worldId, "World", "world", Instant.now())));
        when(recordingService.listRecordings(adventureId)).thenReturn(List.of());
        when(adventureRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Ollama is unreachable - this must not prevent resolving adventures
        // that don't need it at all (e.g. no recordings were ever made).
        when(worldBuildingClient.isReachable()).thenReturn(false);

        WorldFactExtractionService service = new WorldFactExtractionService(adventureRepository, chronicleRepository,
                worldRepository, worldBuildingClient, vaultRepository, recordingService);

        service.retryPending();

        ArgumentCaptor<Adventure> savedCaptor = ArgumentCaptor.forClass(Adventure.class);
        verify(adventureRepository).save(savedCaptor.capture());
        Adventure saved = savedCaptor.getValue();

        assertThat(saved.worldExtractionStatus()).isEqualTo(WorldExtractionStatus.DRAFT_READY);
        assertThat(saved.draftFactsText()).isEqualTo("");
        verify(worldBuildingClient, never()).summarizeFacts(any(), any(), any(), any(), any());
    }
}
