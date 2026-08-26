package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.NpcService;
import de.urr4.rp.roleplayer.web.dto.CreateNpcRequest;
import de.urr4.rp.roleplayer.web.dto.LinkIdRequest;
import de.urr4.rp.roleplayer.web.dto.NpcDto;
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

@RestController
@RequestMapping("/api/chronicles/{chronicleId}/npcs")
public class ChronicleNpcController {

    private final NpcService npcService;

    public ChronicleNpcController(NpcService npcService) {
        this.npcService = npcService;
    }

    @GetMapping
    public List<NpcDto> list(@PathVariable String chronicleId) {
        return npcService.listChronicleNpcs(chronicleId).stream().map(NpcDto::from).toList();
    }

    @PostMapping
    public NpcDto create(@PathVariable String chronicleId, @Valid @RequestBody CreateNpcRequest request) {
        return NpcDto.from(npcService.saveNpcInChronicle(chronicleId, request.name(), request.motive(),
                request.status(), request.mood()));
    }

    @PostMapping("/import")
    public ResponseEntity<Void> importExisting(@PathVariable String chronicleId,
                                               @Valid @RequestBody LinkIdRequest request) {
        npcService.importNpcIntoChronicle(chronicleId, request.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{npcId}")
    public ResponseEntity<Void> remove(@PathVariable String chronicleId, @PathVariable String npcId) {
        npcService.removeNpcFromChronicle(chronicleId, npcId);
        return ResponseEntity.noContent().build();
    }
}
