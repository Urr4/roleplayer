package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.World;
import de.urr4.rp.roleplayer.domain.port.out.WorldRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaWorldAdapter implements WorldRepository {

    private final SpringDataWorldRepository repository;

    public JpaWorldAdapter(SpringDataWorldRepository repository) {
        this.repository = repository;
    }

    @Override
    public World save(World world) {
        WorldEntity saved = repository.save(new WorldEntity(world.id(), world.name(), world.slug(), world.createdAt()));
        return toDomain(saved);
    }

    @Override
    public List<World> findAll() {
        return repository.findAll().stream().map(JpaWorldAdapter::toDomain).toList();
    }

    @Override
    public Optional<World> findById(String id) {
        return repository.findById(id).map(JpaWorldAdapter::toDomain);
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    private static World toDomain(WorldEntity entity) {
        return new World(entity.getId(), entity.getName(), entity.getSlug(), entity.getCreatedAt());
    }
}
