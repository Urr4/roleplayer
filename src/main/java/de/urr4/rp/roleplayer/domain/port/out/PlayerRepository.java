package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Player;

import java.util.List;
import java.util.Optional;

public interface PlayerRepository {
    Player save(Player player);

    List<Player> findAll();

    Optional<Player> findById(String id);

    void deleteById(String id);
}
