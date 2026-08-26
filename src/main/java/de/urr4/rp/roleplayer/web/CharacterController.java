package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.CharacterService;
import de.urr4.rp.roleplayer.web.dto.CharacterDto;
import de.urr4.rp.roleplayer.web.dto.ImportCharacterRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping("/api/characters")
    public List<CharacterDto> listAll() {
        return characterService.listAllCharacters().stream().map(CharacterDto::from).toList();
    }

    @GetMapping("/api/chronicles/{chronicleId}/characters")
    public List<CharacterDto> listByChronicle(@PathVariable String chronicleId) {
        return characterService.listByChronicle(chronicleId).stream().map(CharacterDto::from).toList();
    }

    @PostMapping("/api/chronicles/{chronicleId}/characters")
    public CharacterDto create(@PathVariable String chronicleId, @RequestParam String name, @RequestParam String playerId,
                               @RequestPart(required = false) MultipartFile sheet) {
        return CharacterDto.from(characterService.createCharacter(chronicleId, name, playerId, bytesOf(sheet)));
    }

    @PostMapping("/api/chronicles/{chronicleId}/characters/import")
    public CharacterDto importIntoChronicle(@PathVariable String chronicleId,
                                            @Valid @RequestBody ImportCharacterRequest request) {
        return CharacterDto.from(characterService.importIntoChronicle(request.characterId(), chronicleId));
    }

    @PostMapping("/api/characters/{id}/sheet")
    public CharacterDto replaceSheet(@PathVariable String id, @RequestPart MultipartFile sheet) {
        return CharacterDto.from(characterService.replaceSheet(id, bytesOf(sheet)));
    }

    @GetMapping("/api/characters/{id}/sheet-url")
    public ResponseEntity<SheetUrlResponse> sheetUrl(@PathVariable String id) {
        return characterService.getSheetUrl(id)
                .map(url -> ResponseEntity.ok(new SheetUrlResponse(url)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static byte[] bytesOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record SheetUrlResponse(String url) { }
}
