package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Character;
import de.urr4.rp.roleplayer.domain.port.out.AdventureCharacterRepository;
import de.urr4.rp.roleplayer.domain.port.out.CharacterRepository;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import de.urr4.rp.roleplayer.domain.port.out.PdfStore;
import de.urr4.rp.roleplayer.domain.port.out.PlayerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final ChronicleRepository chronicleRepository;
    private final PlayerRepository playerRepository;
    private final PdfStore pdfStore;
    private final AdventureCharacterRepository adventureCharacterRepository;

    public CharacterService(CharacterRepository characterRepository, ChronicleRepository chronicleRepository,
                            PlayerRepository playerRepository, PdfStore pdfStore,
                            AdventureCharacterRepository adventureCharacterRepository) {
        this.characterRepository = characterRepository;
        this.chronicleRepository = chronicleRepository;
        this.playerRepository = playerRepository;
        this.pdfStore = pdfStore;
        this.adventureCharacterRepository = adventureCharacterRepository;
    }

    public Character createCharacter(String chronicleId, String name, String playerId, byte[] pdfBytes) {
        requireChronicle(chronicleId);
        requirePlayer(playerId);
        String pdfObjectKey = pdfBytes != null && pdfBytes.length > 0 ? pdfStore.store(pdfBytes) : null;
        Character character = new Character(UUID.randomUUID().toString(), chronicleId, name, playerId, pdfObjectKey,
                Instant.now());
        return characterRepository.save(character);
    }

    public Character replaceSheet(String characterId, byte[] pdfBytes) {
        Character existing = requireCharacter(characterId);
        if (existing.pdfObjectKey() != null) {
            pdfStore.delete(existing.pdfObjectKey());
        }
        String newKey = pdfStore.store(pdfBytes);
        Character updated = new Character(existing.id(), existing.chronicleId(), existing.name(), existing.playerId(),
                newKey, existing.createdAt());
        return characterRepository.save(updated);
    }

    public Character importIntoChronicle(String characterId, String targetChronicleId) {
        Character existing = requireCharacter(characterId);
        requireChronicle(targetChronicleId);
        Character cloned = new Character(UUID.randomUUID().toString(), targetChronicleId, existing.name(),
                existing.playerId(), existing.pdfObjectKey(), Instant.now());
        return characterRepository.save(cloned);
    }

    public List<Character> listAllCharacters() {
        return characterRepository.findAll();
    }

    public List<Character> listByChronicle(String chronicleId) {
        requireChronicle(chronicleId);
        return characterRepository.findByChronicleId(chronicleId);
    }

    public Optional<String> getSheetUrl(String characterId) {
        return characterRepository.findById(characterId)
                .map(Character::pdfObjectKey)
                .filter(key -> key != null && !key.isBlank())
                .map(pdfStore::presignedUrl);
    }

    @Transactional
    public void deleteCharacter(String characterId) {
        Character existing = requireCharacter(characterId);
        if (existing.pdfObjectKey() != null && !existing.pdfObjectKey().isBlank()) {
            pdfStore.delete(existing.pdfObjectKey());
        }
        adventureCharacterRepository.deleteByCharacterId(characterId);
        characterRepository.deleteById(characterId);
    }

    private void requireChronicle(String chronicleId) {
        chronicleRepository.findById(chronicleId)
                .orElseThrow(() -> new NoSuchElementException("Chronicle not found: " + chronicleId));
    }

    private void requirePlayer(String playerId) {
        playerRepository.findById(playerId)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + playerId));
    }

    private Character requireCharacter(String characterId) {
        return characterRepository.findById(characterId)
                .orElseThrow(() -> new NoSuchElementException("Character not found: " + characterId));
    }
}
