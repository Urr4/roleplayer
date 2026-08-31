package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.AdventureStatus;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryAdventureRepository implements AdventureRepository {
    private final Map<String, Adventure> store = new ConcurrentHashMap<>();

    @Override
    public Adventure save(Adventure adventure) {
        store.put(adventure.id(), adventure);
        return adventure;
    }

    @Override
    public List<Adventure> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public List<Adventure> findByChronicleId(String chronicleId) {
        return store.values().stream().filter(a -> a.chronicleId().equals(chronicleId)).toList();
    }

    @Override
    public Optional<Adventure> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Adventure> findActiveByChronicleId(String chronicleId) {
        return store.values().stream()
                .filter(a -> a.chronicleId().equals(chronicleId) && a.status() == AdventureStatus.ACTIVE)
                .findFirst();
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}
