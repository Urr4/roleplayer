package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Npc;
import de.urr4.rp.roleplayer.domain.port.out.NpcRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link NpcRepository}, active only in the
 * {@code local} profile. Data is lost on restart.
 */
@Component
@Profile("local")
public class InMemoryNpcRepository implements NpcRepository {

    private final Map<String, Npc> store = new ConcurrentHashMap<>();

    @Override
    public Npc save(Npc npc) {
        store.put(npc.id(), npc);
        return npc;
    }

    @Override
    public List<Npc> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public List<Npc> findByIds(List<String> ids) {
        return ids.stream().map(store::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public Optional<Npc> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
