package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.AdventureCharacter;
import de.urr4.rp.roleplayer.domain.model.Character;
import de.urr4.rp.roleplayer.domain.port.out.AdventureCharacterRepository;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import de.urr4.rp.roleplayer.domain.port.out.CharacterRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class AdventureCharacterService {

    private final AdventureCharacterRepository adventureCharacterRepository;
    private final AdventureRepository adventureRepository;
    private final CharacterRepository characterRepository;

    public AdventureCharacterService(AdventureCharacterRepository adventureCharacterRepository,
                                     AdventureRepository adventureRepository,
                                     CharacterRepository characterRepository) {
        this.adventureCharacterRepository = adventureCharacterRepository;
        this.adventureRepository = adventureRepository;
        this.characterRepository = characterRepository;
    }

    public AdventureCharacter addCharacter(String adventureId, String characterId) {
        Adventure adventure = requireAdventure(adventureId);
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new NoSuchElementException("Character not found: " + characterId));
        if (!adventure.chronicleId().equals(character.chronicleId())) {
            throw new IllegalArgumentException("Character " + characterId + " belongs to chronicle "
                    + character.chronicleId() + " and cannot join adventure " + adventureId
                    + " in chronicle " + adventure.chronicleId());
        }

        return adventureCharacterRepository.findByAdventureIdAndCharacterId(adventureId, characterId)
                .orElseGet(() -> adventureCharacterRepository.save(
                        new AdventureCharacter(UUID.randomUUID().toString(), adventureId, characterId, Instant.now())));
    }

    public void removeCharacter(String adventureId, String characterId) {
        requireAdventure(adventureId);
        adventureCharacterRepository.findByAdventureIdAndCharacterId(adventureId, characterId)
                .ifPresent(link -> adventureCharacterRepository.deleteById(link.id()));
    }

    public List<AdventureCharacter> listForAdventure(String adventureId) {
        requireAdventure(adventureId);
        return adventureCharacterRepository.findByAdventureId(adventureId);
    }

    private Adventure requireAdventure(String adventureId) {
        return adventureRepository.findById(adventureId)
                .orElseThrow(() -> new NoSuchElementException("Adventure not found: " + adventureId));
    }
}
