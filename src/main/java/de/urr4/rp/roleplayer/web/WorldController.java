package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.WorldService;
import de.urr4.rp.roleplayer.web.dto.CreateWorldRequest;
import de.urr4.rp.roleplayer.web.dto.WorldDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/worlds")
public class WorldController {

    private final WorldService worldService;

    public WorldController(WorldService worldService) {
        this.worldService = worldService;
    }

    @GetMapping
    public List<WorldDto> list() {
        return worldService.listWorlds().stream().map(WorldDto::from).toList();
    }

    @PostMapping
    public WorldDto create(@Valid @RequestBody CreateWorldRequest request) {
        return WorldDto.from(worldService.createWorld(request.name()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorldDto> get(@PathVariable String id) {
        try {
            return ResponseEntity.ok(WorldDto.from(worldService.getWorld(id)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
