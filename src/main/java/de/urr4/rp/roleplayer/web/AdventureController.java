package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.AdventureCharacterService;
import de.urr4.rp.roleplayer.application.AdventureService;
import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.web.dto.AdventureDto;
import de.urr4.rp.roleplayer.web.dto.CreateAdventureRequest;
import de.urr4.rp.roleplayer.web.dto.PushWorldFactsRequest;
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

@RestController
public class AdventureController {

    private final AdventureService adventureService;
    private final AdventureCharacterService adventureCharacterService;

    public AdventureController(AdventureService adventureService, AdventureCharacterService adventureCharacterService) {
        this.adventureService = adventureService;
        this.adventureCharacterService = adventureCharacterService;
    }

    @GetMapping("/api/chronicles/{chronicleId}/adventures")
    public List<AdventureDto> listByChronicle(@PathVariable String chronicleId) {
        return adventureService.listByChronicle(chronicleId).stream().map(AdventureDto::from).toList();
    }

    @PostMapping("/api/chronicles/{chronicleId}/adventures")
    public AdventureDto create(@PathVariable String chronicleId, @Valid @RequestBody CreateAdventureRequest request) {
        Adventure adventure = adventureService.createAdventure(chronicleId, request.name());
        if (request.characterIds() != null) {
            request.characterIds().forEach(characterId -> adventureCharacterService.addCharacter(adventure.id(), characterId));
        }
        return AdventureDto.from(adventure);
    }

    @GetMapping("/api/adventures/{id}")
    public ResponseEntity<AdventureDto> get(@PathVariable String id) {
        return adventureService.getAdventure(id)
                .map(AdventureDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/chronicles/{chronicleId}/adventures/active")
    public ResponseEntity<AdventureDto> getActive(@PathVariable String chronicleId) {
        return adventureService.getActiveAdventure(chronicleId)
                .map(AdventureDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/adventures/{id}/start")
    public AdventureDto start(@PathVariable String id) {
        return AdventureDto.from(adventureService.startAdventure(id));
    }

    @PostMapping("/api/adventures/{id}/stop")
    public AdventureDto stop(@PathVariable String id) {
        return AdventureDto.from(adventureService.stopAdventure(id));
    }

    @PostMapping("/api/adventures/{id}/world-facts/push")
    public ResponseEntity<AdventureDto> pushWorldFacts(@PathVariable String id, @Valid @RequestBody PushWorldFactsRequest request) {
        try {
            return ResponseEntity.ok(AdventureDto.from(adventureService.pushWorldFacts(id, request.factsText())));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @DeleteMapping("/api/adventures/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        try {
            adventureService.deleteAdventure(id);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }
}
