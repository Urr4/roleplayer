package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Character;
import de.urr4.rp.roleplayer.domain.port.out.CharacterRepository;
import de.urr4.rp.roleplayer.domain.port.out.PdfStore;
import de.urr4.rp.roleplayer.domain.port.out.SessionCharacterLinkRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final SessionCharacterLinkRepository linkRepository;
    private final PdfStore pdfStore;

    public CharacterService(CharacterRepository characterRepository, SessionCharacterLinkRepository linkRepository,
                             PdfStore pdfStore) {
        this.characterRepository = characterRepository;
        this.linkRepository = linkRepository;
        this.pdfStore = pdfStore;
    }

    public Character createCharacter(String name, String playerId, byte[] pdfBytes) {
        String pdfObjectKey = pdfBytes != null && pdfBytes.length > 0 ? pdfStore.store(pdfBytes) : null;
        Character character = new Character(UUID.randomUUID().toString(), name, playerId, pdfObjectKey,
                Instant.now());
        return characterRepository.save(character);
    }

    public Character replaceSheet(String characterId, byte[] pdfBytes) {
        Character existing = characterRepository.findById(characterId)
                .orElseThrow(() -> new NoSuchElementException("Character not found: " + characterId));
        if (existing.pdfObjectKey() != null) {
            pdfStore.delete(existing.pdfObjectKey());
        }
        String newKey = pdfStore.store(pdfBytes);
        Character updated = new Character(existing.id(), existing.name(), existing.playerId(), newKey,
                existing.createdAt());
        return characterRepository.save(updated);
    }

    public List<Character> listAllCharacters() {
        return characterRepository.findAll();
    }

    public List<Character> listSessionCharacters(String sessionId) {
        List<String> ids = linkRepository.findCharacterIdsBySession(sessionId);
        return characterRepository.findByIds(ids);
    }

    public void linkToSession(String sessionId, String characterId) {
        linkRepository.link(sessionId, characterId);
    }

    public void unlinkFromSession(String sessionId, String characterId) {
        linkRepository.unlink(sessionId, characterId);
    }

    public Optional<String> getSheetUrl(String characterId) {
        return characterRepository.findById(characterId)
                .map(Character::pdfObjectKey)
                .filter(key -> key != null && !key.isBlank())
                .map(pdfStore::presignedUrl);
    }
}
