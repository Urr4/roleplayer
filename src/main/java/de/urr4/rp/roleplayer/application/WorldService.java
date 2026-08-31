package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.World;
import de.urr4.rp.roleplayer.domain.port.out.WorldRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class WorldService {

    private final WorldRepository worldRepository;

    public WorldService(WorldRepository worldRepository) {
        this.worldRepository = worldRepository;
    }

    public World createWorld(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("World name must not be blank");
        }
        World world = new World(UUID.randomUUID().toString(), name.trim(), Slugs.worldSlug(name), Instant.now());
        return worldRepository.save(world);
    }

    public List<World> listWorlds() {
        return worldRepository.findAll();
    }

    public World getWorld(String id) {
        return worldRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("World not found: " + id));
    }
}
