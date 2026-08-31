package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.Character;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import de.urr4.rp.roleplayer.domain.port.out.CharacterRepository;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleNpcLinkRepository;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import de.urr4.rp.roleplayer.domain.port.out.WorldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChronicleService {

    private final ChronicleRepository chronicleRepository;
    private final AdventureRepository adventureRepository;
    private final AdventureService adventureService;
    private final CharacterRepository characterRepository;
    private final CharacterService characterService;
    private final ChronicleNpcLinkRepository chronicleNpcLinkRepository;
    private final WorldRepository worldRepository;

    public ChronicleService(ChronicleRepository chronicleRepository, AdventureRepository adventureRepository,
                            AdventureService adventureService, CharacterRepository characterRepository,
                            CharacterService characterService, ChronicleNpcLinkRepository chronicleNpcLinkRepository,
                            WorldRepository worldRepository) {
        this.chronicleRepository = chronicleRepository;
        this.adventureRepository = adventureRepository;
        this.adventureService = adventureService;
        this.characterRepository = characterRepository;
        this.characterService = characterService;
        this.chronicleNpcLinkRepository = chronicleNpcLinkRepository;
        this.worldRepository = worldRepository;
    }

    public Chronicle createChronicle(String name, String worldId) {
        worldRepository.findById(worldId)
                .orElseThrow(() -> new NoSuchElementException("World not found: " + worldId));
        Chronicle chronicle = new Chronicle(UUID.randomUUID().toString(), name, Instant.now(), worldId);
        return chronicleRepository.save(chronicle);
    }

    public List<Chronicle> listChronicles() {
        return chronicleRepository.findAll();
    }

    public Optional<Chronicle> getChronicle(String id) {
        return chronicleRepository.findById(id);
    }

    @Transactional
    public void deleteChronicle(String id) {
        chronicleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Chronicle not found: " + id));
        for (Adventure adventure : adventureRepository.findByChronicleId(id)) {
            adventureService.deleteAdventure(adventure.id());
        }
        for (Character character : characterRepository.findByChronicleId(id)) {
            characterService.deleteCharacter(character.id());
        }
        chronicleNpcLinkRepository.unlinkAll(id);
        chronicleRepository.deleteById(id);
    }
}
