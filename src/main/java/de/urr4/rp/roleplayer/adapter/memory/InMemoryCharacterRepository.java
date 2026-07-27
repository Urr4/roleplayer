package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Character;
import de.urr4.rp.roleplayer.domain.port.out.CharacterRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link CharacterRepository}, active only in the
 * {@code local} profile. Data is lost on restart.
 */
@Component
@Profile("local")
public class InMemoryCharacterRepository implements CharacterRepository {

    private final Map<String, Character> store = new ConcurrentHashMap<>();

    @Override
    public Character save(Character character) {
        store.put(character.id(), character);
        return character;
    }

    @Override
    public List<Character> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public List<Character> findByIds(List<String> ids) {
        return ids.stream().map(store::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public Optional<Character> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
