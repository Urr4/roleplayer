package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.World;

import java.util.List;
import java.util.Optional;

public interface WorldRepository {
    World save(World world);

    List<World> findAll();

    Optional<World> findById(String id);

    void deleteById(String id);
}
