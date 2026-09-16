package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.NpcService;
import de.urr4.rp.roleplayer.web.dto.GenerateNpcRequest;
import de.urr4.rp.roleplayer.web.dto.NpcDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/npcs")
public class NpcController {

    private final NpcService npcService;

    public NpcController(NpcService npcService) {
        this.npcService = npcService;
    }

    @GetMapping
    public List<NpcDto> listAll() {
        return npcService.listAllNpcs().stream().map(NpcDto::from).toList();
    }

    /** Generates a brand-new, unsaved NPC sheet via Ollama from a short description — used by the "Generate with AI" button. */
    @PostMapping("/generate")
    public NpcDto generate(@Valid @RequestBody GenerateNpcRequest request) {
        return NpcDto.from(npcService.generateNpc(request.name(), request.description()));
    }
}
