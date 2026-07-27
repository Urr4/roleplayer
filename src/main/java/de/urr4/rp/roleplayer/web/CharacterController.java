package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.CharacterService;
import de.urr4.rp.roleplayer.web.dto.CharacterDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterService characterService;

    public CharacterController(CharacterService characterService) {
        this.characterService = characterService;
    }

    @GetMapping
    public List<CharacterDto> listAll() {
        return characterService.listAllCharacters().stream().map(CharacterDto::from).toList();
    }

    @PostMapping
    public CharacterDto create(@RequestParam String name, @RequestParam String playerId,
                                @RequestPart(required = false) MultipartFile sheet) {
        return CharacterDto.from(characterService.createCharacter(name, playerId, bytesOf(sheet)));
    }

    @PostMapping("/{id}/sheet")
    public CharacterDto replaceSheet(@PathVariable String id, @RequestPart MultipartFile sheet) {
        return CharacterDto.from(characterService.replaceSheet(id, bytesOf(sheet)));
    }

    @GetMapping("/{id}/sheet-url")
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

    public record SheetUrlResponse(String url) {
    }
}
