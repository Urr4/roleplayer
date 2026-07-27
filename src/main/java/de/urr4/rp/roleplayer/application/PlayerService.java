package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Player;
import de.urr4.rp.roleplayer.domain.port.out.PlayerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;

    public PlayerService(PlayerRepository playerRepository) {
        this.playerRepository = playerRepository;
    }

    public Player createPlayer(String name) {
        Player player = new Player(UUID.randomUUID().toString(), name);
        return playerRepository.save(player);
    }

    public List<Player> listPlayers() {
        return playerRepository.findAll();
    }
}
