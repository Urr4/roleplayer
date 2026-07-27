package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.Npc;
import de.urr4.rp.roleplayer.domain.port.out.NpcRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaNpcAdapter implements NpcRepository {

    private final SpringDataNpcRepository repository;

    public JpaNpcAdapter(SpringDataNpcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Npc save(Npc npc) {
        NpcEntity saved = repository.save(new NpcEntity(npc.id(), npc.name(), npc.motive(), npc.status(),
                npc.mood(), npc.originSessionId(), npc.createdAt()));
        return toDomain(saved);
    }

    @Override
    public List<Npc> findAll() {
        return repository.findAll().stream().map(JpaNpcAdapter::toDomain).toList();
    }

    @Override
    public List<Npc> findByIds(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findByIdIn(ids).stream().map(JpaNpcAdapter::toDomain).toList();
    }

    @Override
    public Optional<Npc> findById(String id) {
        return repository.findById(id).map(JpaNpcAdapter::toDomain);
    }

    private static Npc toDomain(NpcEntity entity) {
        return new Npc(entity.getId(), entity.getName(), entity.getMotive(), entity.getStatus(), entity.getMood(),
                entity.getOriginSessionId(), entity.getCreatedAt());
    }
}
