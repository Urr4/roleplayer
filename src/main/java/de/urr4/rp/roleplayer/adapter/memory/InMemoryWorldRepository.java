package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.World;
import de.urr4.rp.roleplayer.domain.port.out.WorldRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryWorldRepository implements WorldRepository {

    private final Map<String, World> store = new ConcurrentHashMap<>();

    @Override
    public World save(World world) {
        store.put(world.id(), world);
        return world;
    }

    @Override
    public List<World> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public Optional<World> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void deleteById(String id) {
        store.remove(id);
    }
}
