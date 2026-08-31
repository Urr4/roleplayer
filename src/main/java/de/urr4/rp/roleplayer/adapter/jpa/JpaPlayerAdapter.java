package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.Player;
import de.urr4.rp.roleplayer.domain.port.out.PlayerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaPlayerAdapter implements PlayerRepository {

    private final SpringDataPlayerRepository repository;

    public JpaPlayerAdapter(SpringDataPlayerRepository repository) {
        this.repository = repository;
    }

    @Override
    public Player save(Player player) {
        PlayerEntity saved = repository.save(new PlayerEntity(player.id(), player.name()));
        return toDomain(saved);
    }

    @Override
    public List<Player> findAll() {
        return repository.findAll().stream().map(JpaPlayerAdapter::toDomain).toList();
    }

    @Override
    public Optional<Player> findById(String id) {
        return repository.findById(id).map(JpaPlayerAdapter::toDomain);
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    private static Player toDomain(PlayerEntity entity) {
        return new Player(entity.getId(), entity.getName());
    }
}
