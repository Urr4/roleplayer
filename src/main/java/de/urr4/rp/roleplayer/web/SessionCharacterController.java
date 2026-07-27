package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.CharacterService;
import de.urr4.rp.roleplayer.web.dto.CharacterDto;
import de.urr4.rp.roleplayer.web.dto.LinkIdRequest;
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
@RequestMapping("/api/sessions/{sessionId}/characters")
public class SessionCharacterController {

    private final CharacterService characterService;

    public SessionCharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping
    public List<CharacterDto> list(@PathVariable String sessionId) {
        return characterService.listSessionCharacters(sessionId).stream().map(CharacterDto::from).toList();
    }

    @PostMapping
    public ResponseEntity<Void> link(@PathVariable String sessionId, @Valid @RequestBody LinkIdRequest request) {
        characterService.linkToSession(sessionId, request.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{characterId}")
    public ResponseEntity<Void> unlink(@PathVariable String sessionId, @PathVariable String characterId) {
        characterService.unlinkFromSession(sessionId, characterId);
        return ResponseEntity.noContent().build();
    }
}
