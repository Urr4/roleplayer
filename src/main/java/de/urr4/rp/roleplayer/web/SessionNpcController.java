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
@RequestMapping("/api/sessions/{sessionId}/npcs")
public class SessionNpcController {

    private final NpcService npcService;

    public SessionNpcController(NpcService npcService) {
        this.npcService = npcService;
    }

    @GetMapping
    public List<NpcDto> list(@PathVariable String sessionId) {
        return npcService.listSessionNpcs(sessionId).stream().map(NpcDto::from).toList();
    }

    @PostMapping
    public NpcDto create(@PathVariable String sessionId, @Valid @RequestBody CreateNpcRequest request) {
        return NpcDto.from(npcService.saveNpcInSession(sessionId, request.name(), request.motive(),
                request.status(), request.mood()));
    }

    @PostMapping("/import")
    public ResponseEntity<Void> importExisting(@PathVariable String sessionId,
                                                @Valid @RequestBody LinkIdRequest request) {
        npcService.importNpcIntoSession(sessionId, request.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{npcId}")
    public ResponseEntity<Void> remove(@PathVariable String sessionId, @PathVariable String npcId) {
        npcService.removeNpcFromSession(sessionId, npcId);
        return ResponseEntity.noContent().build();
    }
}
