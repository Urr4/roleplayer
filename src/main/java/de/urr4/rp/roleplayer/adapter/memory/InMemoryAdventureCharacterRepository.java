package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.AdventureCharacter;
import de.urr4.rp.roleplayer.domain.port.out.AdventureCharacterRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryAdventureCharacterRepository implements AdventureCharacterRepository {
    private final Map<String, AdventureCharacter> store = new ConcurrentHashMap<>();

    @Override
    public AdventureCharacter save(AdventureCharacter adventureCharacter) {
        store.put(adventureCharacter.id(), adventureCharacter);
        return adventureCharacter;
    }

    @Override
    public List<AdventureCharacter> findByAdventureId(String adventureId) {
        return store.values().stream()
                .filter(link -> link.adventureId().equals(adventureId))
                .sorted(Comparator.comparing(AdventureCharacter::addedAt))
                .toList();
    }

    @Override
    public Optional<AdventureCharacter> findByAdventureIdAndCharacterId(String adventureId, String characterId) {
        return store.values().stream()
                .filter(link -> link.adventureId().equals(adventureId) && link.characterId().equals(characterId))
                .findFirst();
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}
