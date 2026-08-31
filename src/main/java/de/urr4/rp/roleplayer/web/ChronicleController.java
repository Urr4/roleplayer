package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.ChronicleService;
import de.urr4.rp.roleplayer.application.WorldService;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.model.World;
import de.urr4.rp.roleplayer.web.dto.ChronicleDto;
import de.urr4.rp.roleplayer.web.dto.CreateChronicleRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/chronicles")
public class ChronicleController {

    private final ChronicleService chronicleService;
    private final WorldService worldService;

    public ChronicleController(ChronicleService chronicleService, WorldService worldService) {
        this.chronicleService = chronicleService;
        this.worldService = worldService;
    }

    @GetMapping
    public List<ChronicleDto> list() {
        return chronicleService.listChronicles().stream().map(this::toDto).toList();
    }

    @PostMapping
    public ChronicleDto create(@Valid @RequestBody CreateChronicleRequest request) {
        return toDto(chronicleService.createChronicle(request.name(), request.worldId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChronicleDto> get(@PathVariable String id) {
        return chronicleService.getChronicle(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        try {
            chronicleService.deleteChronicle(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    private ChronicleDto toDto(Chronicle chronicle) {
        if (chronicle.worldId() == null) {
            return ChronicleDto.from(chronicle, null, null);
        }
        Optional<World> world = chronicleService.getChronicle(chronicle.id())
                .flatMap(c -> c.worldId() == null ? Optional.empty() : Optional.ofNullable(findWorld(c.worldId())));
        return ChronicleDto.from(chronicle, world.map(World::name).orElse(null), world.map(World::slug).orElse(null));
    }

    private World findWorld(String worldId) {
        try {
            return worldService.getWorld(worldId);
        } catch (NoSuchElementException e) {
            return null;
        }
    }
}
