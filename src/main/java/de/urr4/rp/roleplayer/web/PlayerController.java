package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.PlayerService;
import de.urr4.rp.roleplayer.web.dto.CreatePlayerRequest;
import de.urr4.rp.roleplayer.web.dto.PlayerDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @GetMapping
    public List<PlayerDto> list() {
        return playerService.listPlayers().stream().map(PlayerDto::from).toList();
    }

    @PostMapping
    public PlayerDto create(@Valid @RequestBody CreatePlayerRequest request) {
        return PlayerDto.from(playerService.createPlayer(request.name()));
    }

    @DeleteMapping("/{id}")
    public org.springframework.http.ResponseEntity<Void> delete(@PathVariable String id) {
        playerService.deletePlayer(id);
        return org.springframework.http.ResponseEntity.noContent().build();
    }
}
