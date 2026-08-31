package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryChronicleRepository implements ChronicleRepository {

    private final Map<String, Chronicle> store = new ConcurrentHashMap<>();

    @Override
    public Chronicle save(Chronicle chronicle) {
        store.put(chronicle.id(), chronicle);
        return chronicle;
    }

    @Override
    public List<Chronicle> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public Optional<Chronicle> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}
