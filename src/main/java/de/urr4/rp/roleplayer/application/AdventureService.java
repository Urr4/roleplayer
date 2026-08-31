package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.AdventureStatus;
import de.urr4.rp.roleplayer.domain.port.out.AdventureCharacterRepository;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdventureService {

    private final AdventureRepository adventureRepository;
    private final ChronicleRepository chronicleRepository;
    private final AdventureCharacterRepository adventureCharacterRepository;
    private final RecordingService recordingService;

    public AdventureService(AdventureRepository adventureRepository, ChronicleRepository chronicleRepository,
                            AdventureCharacterRepository adventureCharacterRepository,
                            RecordingService recordingService) {
        this.adventureRepository = adventureRepository;
        this.chronicleRepository = chronicleRepository;
        this.adventureCharacterRepository = adventureCharacterRepository;
        this.recordingService = recordingService;
    }

    public Adventure createAdventure(String chronicleId, String name) {
        chronicleRepository.findById(chronicleId)
                .orElseThrow(() -> new NoSuchElementException("Chronicle not found: " + chronicleId));
        Adventure adventure = new Adventure(
                UUID.randomUUID().toString(), chronicleId, name, AdventureStatus.PLANNED, Instant.now(), null, null);
        return adventureRepository.save(adventure);
    }

    public List<Adventure> listAdventures() {
        return adventureRepository.findAll();
    }

    public List<Adventure> listByChronicle(String chronicleId) {
        return adventureRepository.findByChronicleId(chronicleId);
    }

    public Optional<Adventure> getAdventure(String id) {
        return adventureRepository.findById(id);
    }

    public Optional<Adventure> getActiveAdventure(String chronicleId) {
        return adventureRepository.findActiveByChronicleId(chronicleId);
    }

    public Adventure startAdventure(String id) {
        Adventure adventure = adventureRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Adventure not found: " + id));
        if (adventure.status() == AdventureStatus.ACTIVE) {
            return adventure;
        }
        adventureRepository.findActiveByChronicleId(adventure.chronicleId()).ifPresent(active -> {
            if (!active.id().equals(id)) {
                throw new IllegalStateException("Another adventure is already active in this chronicle: " + active.name());
            }
        });
        Instant startedAt = adventure.startedAt() == null ? Instant.now() : adventure.startedAt();
        Adventure started = new Adventure(
                adventure.id(), adventure.chronicleId(), adventure.name(), AdventureStatus.ACTIVE,
                adventure.createdAt(), startedAt, null);
        return adventureRepository.save(started);
    }

    public Adventure stopAdventure(String id) {
        Adventure adventure = adventureRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Adventure not found: " + id));
        Adventure stopped = new Adventure(
                adventure.id(), adventure.chronicleId(), adventure.name(), AdventureStatus.COMPLETED,
                adventure.createdAt(), adventure.startedAt(), Instant.now());
        return adventureRepository.save(stopped);
    }

    public void deleteAdventure(String id) {
        adventureRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Adventure not found: " + id));
        recordingService.deleteRecordingsByAdventureId(id);
        adventureCharacterRepository.deleteByAdventureId(id);
        adventureRepository.deleteById(id);
    }
}
