package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.NpcService;
import de.urr4.rp.roleplayer.domain.model.NpcAttributePools;
import de.urr4.rp.roleplayer.domain.model.NpcStatus;
import de.urr4.rp.roleplayer.web.dto.NpcDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /** Rolls a brand-new, unsaved random NPC — used by the "Random NPC" button. */
    @GetMapping("/random")
    public NpcDto random(@RequestParam(required = false) String name) {
        return NpcDto.from(npcService.rollRandomNpc(name));
    }

    @GetMapping("/attribute-pools")
    public AttributePools attributePools() {
        return new AttributePools(NpcAttributePools.MOTIVES, NpcAttributePools.MOODS, NpcStatus.values());
    }

    public record AttributePools(List<String> motives, List<String> moods, NpcStatus[] statuses) {
    }
}
