package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.ChronicleService;
import de.urr4.rp.roleplayer.web.dto.CreateChronicleRequest;
import de.urr4.rp.roleplayer.web.dto.ChronicleDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chronicles")
public class ChronicleController {

    private final ChronicleService chronicleService;

    public ChronicleController(ChronicleService chronicleService) {
        this.chronicleService = chronicleService;
    }

    @GetMapping
    public List<ChronicleDto> list() {
        return chronicleService.listChronicles().stream().map(ChronicleDto::from).toList();
    }

    @PostMapping
    public ChronicleDto create(@Valid @RequestBody CreateChronicleRequest request) {
        return ChronicleDto.from(chronicleService.createChronicle(request.name()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChronicleDto> get(@PathVariable String id) {
        return chronicleService.getChronicle(id)
                .map(ChronicleDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
