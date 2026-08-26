package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.AdventureCharacterService;
import de.urr4.rp.roleplayer.web.dto.AddAdventureCharacterRequest;
import de.urr4.rp.roleplayer.web.dto.AdventureCharacterDto;
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
@RequestMapping("/api/adventures/{adventureId}/characters")
public class AdventureCharacterController {

    private final AdventureCharacterService adventureCharacterService;

    public AdventureCharacterController(AdventureCharacterService adventureCharacterService) {
        this.adventureCharacterService = adventureCharacterService;
    }

    @GetMapping
    public List<AdventureCharacterDto> list(@PathVariable String adventureId) {
        return adventureCharacterService.listForAdventure(adventureId).stream().map(AdventureCharacterDto::from).toList();
    }

    @PostMapping
    public AdventureCharacterDto add(@PathVariable String adventureId, @Valid @RequestBody AddAdventureCharacterRequest request) {
        return AdventureCharacterDto.from(adventureCharacterService.addCharacter(adventureId, request.characterId()));
    }

    @DeleteMapping("/{characterId}")
    public ResponseEntity<Void> remove(@PathVariable String adventureId, @PathVariable String characterId) {
        adventureCharacterService.removeCharacter(adventureId, characterId);
        return ResponseEntity.noContent().build();
    }
}
