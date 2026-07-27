package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Player;
import de.urr4.rp.roleplayer.domain.port.out.PlayerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link PlayerRepository}, active only in the
 * {@code local} profile. Data is lost on restart.
 */
@Component
@Profile("local")
public class InMemoryPlayerRepository implements PlayerRepository {

    private final Map<String, Player> store = new ConcurrentHashMap<>();

    @Override
    public Player save(Player player) {
        store.put(player.id(), player);
        return player;
    }

    @Override
    public List<Player> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public Optional<Player> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
