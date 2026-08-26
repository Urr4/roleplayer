from pathlib import Path
root = Path('/Users/stefan.schubert/Code/Playground/roleplayer/src/main/java/de/urr4/rp/roleplayer')

def write(rel, text):
    p = root / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text.strip() + '\n')

def delete(rel):
    p = root / rel
    if p.exists():
        p.unlink()

write('domain/model/Chronicle.java', '''
package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Chronicle(String id, String name, Instant createdAt) {
}
''')
write('domain/model/Adventure.java', '''
package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Adventure(String id, String chronicleId, String name, Instant createdAt) {
}
''')
write('domain/model/Character.java', '''
package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Character(String id, String chronicleId, String name, String playerId, String pdfObjectKey, Instant createdAt) {
}
''')
write('domain/model/CharacterAssignment.java', '''
package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record CharacterAssignment(String id, String adventureId, String playerId, String characterId, Instant startedAt,
                                  Instant endedAt) {
}
''')
write('domain/model/Npc.java', '''
package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Npc(String id, String name, String motive, NpcStatus status, String mood, String originChronicleId,
                  Instant createdAt) {
}
''')
write('domain/model/Recording.java', '''
package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;

public record Recording(String id, String chronicleId, RecordingSource source, RecordingStatus status, Instant startedAt,
                        Instant endedAt, String audioObjectKey, String transcriptObjectKey) {
}
''')
write('domain/port/out/ChronicleRepository.java', '''
package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Chronicle;

import java.util.List;
import java.util.Optional;

public interface ChronicleRepository {
    Chronicle save(Chronicle chronicle);

    List<Chronicle> findAll();

    Optional<Chronicle> findById(String id);
}
''')
write('domain/port/out/AdventureRepository.java', '''
package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Adventure;

import java.util.List;
import java.util.Optional;

public interface AdventureRepository {
    Adventure save(Adventure adventure);

    List<Adventure> findAll();

    List<Adventure> findByChronicleId(String chronicleId);

    Optional<Adventure> findById(String id);
}
''')
write('domain/port/out/CharacterAssignmentRepository.java', '''
package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.CharacterAssignment;

import java.util.List;
import java.util.Optional;

public interface CharacterAssignmentRepository {
    CharacterAssignment save(CharacterAssignment assignment);

    Optional<CharacterAssignment> findById(String id);

    List<CharacterAssignment> findByAdventureId(String adventureId);

    List<CharacterAssignment> findByAdventureIdAndEndedAtIsNull(String adventureId);

    Optional<CharacterAssignment> findActiveByAdventureIdAndPlayerId(String adventureId, String playerId);
}
''')
write('domain/port/out/CharacterRepository.java', '''
package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Character;

import java.util.List;
import java.util.Optional;

public interface CharacterRepository {
    Character save(Character character);

    List<Character> findAll();

    List<Character> findByChronicleId(String chronicleId);

    Optional<Character> findById(String id);
}
''')
write('domain/port/out/ChronicleNpcLinkRepository.java', '''
package de.urr4.rp.roleplayer.domain.port.out;

import java.util.List;

public interface ChronicleNpcLinkRepository {
    void link(String chronicleId, String npcId);

    void unlink(String chronicleId, String npcId);

    List<String> findNpcIdsByChronicle(String chronicleId);
}
''')
write('domain/port/out/RecordingRepository.java', '''
package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Recording;

import java.util.List;
import java.util.Optional;

public interface RecordingRepository {
    Recording save(Recording recording);

    List<Recording> findByChronicleId(String chronicleId);

    Optional<Recording> findById(String id);
}
''')
write('application/ChronicleService.java', '''
package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChronicleService {

    private final ChronicleRepository chronicleRepository;

    public ChronicleService(ChronicleRepository chronicleRepository) {
        this.chronicleRepository = chronicleRepository;
    }

    public Chronicle createChronicle(String name) {
        Chronicle chronicle = new Chronicle(UUID.randomUUID().toString(), name, Instant.now());
        return chronicleRepository.save(chronicle);
    }

    public List<Chronicle> listChronicles() {
        return chronicleRepository.findAll();
    }

    public Optional<Chronicle> getChronicle(String id) {
        return chronicleRepository.findById(id);
    }
}
''')
write('application/AdventureService.java', '''
package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdventureService {

    private final AdventureRepository adventureRepository;
    private final ChronicleRepository chronicleRepository;

    public AdventureService(AdventureRepository adventureRepository, ChronicleRepository chronicleRepository) {
        this.adventureRepository = adventureRepository;
        this.chronicleRepository = chronicleRepository;
    }

    public Adventure createAdventure(String chronicleId, String name) {
        chronicleRepository.findById(chronicleId)
                .orElseThrow(() -> new NoSuchElementException("Chronicle not found: " + chronicleId));
        Adventure adventure = new Adventure(UUID.randomUUID().toString(), chronicleId, name, Instant.now());
        return adventureRepository.save(adventure);
    }

    public List<Adventure> listAdventures() {
        return adventureRepository.findAll();
    }

    public List<Adventure> listByChronicle(String chronicleId) {
        return adventureRepository.findByChronicleId(chronicleId);
    }

    public Optional<Adventure> getAdventure(String id) {
        return adventureRepository.findById(id);
    }
}
''')
write('application/CharacterService.java', '''
package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Character;
import de.urr4.rp.roleplayer.domain.port.out.CharacterRepository;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import de.urr4.rp.roleplayer.domain.port.out.PdfStore;
import de.urr4.rp.roleplayer.domain.port.out.PlayerRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final ChronicleRepository chronicleRepository;
    private final PlayerRepository playerRepository;
    private final PdfStore pdfStore;

    public CharacterService(CharacterRepository characterRepository, ChronicleRepository chronicleRepository,
                            PlayerRepository playerRepository, PdfStore pdfStore) {
        this.characterRepository = characterRepository;
        this.chronicleRepository = chronicleRepository;
        this.playerRepository = playerRepository;
        this.pdfStore = pdfStore;
    }

    public Character createCharacter(String chronicleId, String name, String playerId, byte[] pdfBytes) {
        requireChronicle(chronicleId);
        requirePlayer(playerId);
        String pdfObjectKey = pdfBytes != null && pdfBytes.length > 0 ? pdfStore.store(pdfBytes) : null;
        Character character = new Character(UUID.randomUUID().toString(), chronicleId, name, playerId, pdfObjectKey,
                Instant.now());
        return characterRepository.save(character);
    }

    public Character replaceSheet(String characterId, byte[] pdfBytes) {
        Character existing = requireCharacter(characterId);
        if (existing.pdfObjectKey() != null) {
            pdfStore.delete(existing.pdfObjectKey());
        }
        String newKey = pdfStore.store(pdfBytes);
        Character updated = new Character(existing.id(), existing.chronicleId(), existing.name(), existing.playerId(),
                newKey, existing.createdAt());
        return characterRepository.save(updated);
    }

    public Character importIntoChronicle(String characterId, String targetChronicleId) {
        Character existing = requireCharacter(characterId);
        requireChronicle(targetChronicleId);
        Character cloned = new Character(UUID.randomUUID().toString(), targetChronicleId, existing.name(),
                existing.playerId(), existing.pdfObjectKey(), Instant.now());
        return characterRepository.save(cloned);
    }

    public List<Character> listAllCharacters() {
        return characterRepository.findAll();
    }

    public List<Character> listByChronicle(String chronicleId) {
        requireChronicle(chronicleId);
        return characterRepository.findByChronicleId(chronicleId);
    }

    public Optional<String> getSheetUrl(String characterId) {
        return characterRepository.findById(characterId)
                .map(Character::pdfObjectKey)
                .filter(key -> key != null && !key.isBlank())
                .map(pdfStore::presignedUrl);
    }

    private void requireChronicle(String chronicleId) {
        chronicleRepository.findById(chronicleId)
                .orElseThrow(() -> new NoSuchElementException("Chronicle not found: " + chronicleId));
    }

    private void requirePlayer(String playerId) {
        playerRepository.findById(playerId)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + playerId));
    }

    private Character requireCharacter(String characterId) {
        return characterRepository.findById(characterId)
                .orElseThrow(() -> new NoSuchElementException("Character not found: " + characterId));
    }
}
''')
write('application/CharacterAssignmentService.java', '''
package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.Character;
import de.urr4.rp.roleplayer.domain.model.CharacterAssignment;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import de.urr4.rp.roleplayer.domain.port.out.CharacterAssignmentRepository;
import de.urr4.rp.roleplayer.domain.port.out.CharacterRepository;
import de.urr4.rp.roleplayer.domain.port.out.PlayerRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CharacterAssignmentService {

    private final CharacterAssignmentRepository assignmentRepository;
    private final AdventureRepository adventureRepository;
    private final PlayerRepository playerRepository;
    private final CharacterRepository characterRepository;

    public CharacterAssignmentService(CharacterAssignmentRepository assignmentRepository,
                                      AdventureRepository adventureRepository,
                                      PlayerRepository playerRepository,
                                      CharacterRepository characterRepository) {
        this.assignmentRepository = assignmentRepository;
        this.adventureRepository = adventureRepository;
        this.playerRepository = playerRepository;
        this.characterRepository = characterRepository;
    }

    public CharacterAssignment assign(String adventureId, String playerId, String characterId) {
        Adventure adventure = requireAdventure(adventureId);
        playerRepository.findById(playerId)
                .orElseThrow(() -> new NoSuchElementException("Player not found: " + playerId));
        Character character = characterRepository.findById(characterId)
                .orElseThrow(() -> new NoSuchElementException("Character not found: " + characterId));
        if (!adventure.chronicleId().equals(character.chronicleId())) {
            throw new IllegalArgumentException("Character " + characterId + " belongs to chronicle "
                    + character.chronicleId() + " and cannot be assigned to adventure " + adventureId
                    + " in chronicle " + adventure.chronicleId());
        }

        Instant now = Instant.now();
        assignmentRepository.findActiveByAdventureIdAndPlayerId(adventureId, playerId)
                .ifPresent(active -> assignmentRepository.save(new CharacterAssignment(active.id(), active.adventureId(),
                        active.playerId(), active.characterId(), active.startedAt(), now)));

        CharacterAssignment assignment = new CharacterAssignment(UUID.randomUUID().toString(), adventureId, playerId,
                characterId, now, null);
        return assignmentRepository.save(assignment);
    }

    public CharacterAssignment endAssignment(String assignmentId) {
        CharacterAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NoSuchElementException("Character assignment not found: " + assignmentId));
        if (assignment.endedAt() != null) {
            return assignment;
        }
        return assignmentRepository.save(new CharacterAssignment(assignment.id(), assignment.adventureId(),
                assignment.playerId(), assignment.characterId(), assignment.startedAt(), Instant.now()));
    }

    public List<CharacterAssignment> listForAdventure(String adventureId) {
        requireAdventure(adventureId);
        return assignmentRepository.findByAdventureId(adventureId);
    }

    public List<CharacterAssignment> listActiveForAdventure(String adventureId) {
        requireAdventure(adventureId);
        return assignmentRepository.findByAdventureIdAndEndedAtIsNull(adventureId);
    }

    private Adventure requireAdventure(String adventureId) {
        return adventureRepository.findById(adventureId)
                .orElseThrow(() -> new NoSuchElementException("Adventure not found: " + adventureId));
    }
}
''')
write('application/NpcService.java', '''
package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Npc;
import de.urr4.rp.roleplayer.domain.model.NpcAttributePools;
import de.urr4.rp.roleplayer.domain.model.NpcStatus;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleNpcLinkRepository;
import de.urr4.rp.roleplayer.domain.port.out.NpcRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NpcService {

    private final NpcRepository npcRepository;
    private final ChronicleNpcLinkRepository linkRepository;

    public NpcService(NpcRepository npcRepository, ChronicleNpcLinkRepository linkRepository) {
        this.npcRepository = npcRepository;
        this.linkRepository = linkRepository;
    }

    public Npc rollRandomNpc(String name) {
        return new Npc(null, name == null || name.isBlank() ? "Unnamed Stranger" : name,
                NpcAttributePools.randomMotive(), NpcAttributePools.randomStatus(), NpcAttributePools.randomMood(),
                null, null);
    }

    public String randomMotive() { return NpcAttributePools.randomMotive(); }
    public String randomMood() { return NpcAttributePools.randomMood(); }
    public NpcStatus randomStatus() { return NpcAttributePools.randomStatus(); }

    public Npc saveNpcInChronicle(String chronicleId, String name, String motive, NpcStatus status, String mood) {
        Npc npc = new Npc(UUID.randomUUID().toString(), name, motive, status, mood, chronicleId, Instant.now());
        Npc saved = npcRepository.save(npc);
        linkRepository.link(chronicleId, saved.id());
        return saved;
    }

    public List<Npc> listChronicleNpcs(String chronicleId) {
        List<String> ids = linkRepository.findNpcIdsByChronicle(chronicleId);
        return npcRepository.findByIds(ids);
    }

    public List<Npc> listAllNpcs() { return npcRepository.findAll(); }

    public void importNpcIntoChronicle(String chronicleId, String npcId) { linkRepository.link(chronicleId, npcId); }

    public void removeNpcFromChronicle(String chronicleId, String npcId) { linkRepository.unlink(chronicleId, npcId); }
}
''')
write('web/dto/ChronicleDto.java', '''
package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Chronicle;

import java.time.Instant;

public record ChronicleDto(String id, String name, Instant createdAt) {
    public static ChronicleDto from(Chronicle chronicle) {
        return new ChronicleDto(chronicle.id(), chronicle.name(), chronicle.createdAt());
    }
}
''')
write('web/dto/CreateChronicleRequest.java', '''
package de.urr4.rp.roleplayer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChronicleRequest(@NotBlank String name) {
}
''')
write('web/dto/AdventureDto.java', '''
package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Adventure;

import java.time.Instant;

public record AdventureDto(String id, String chronicleId, String name, Instant createdAt) {
    public static AdventureDto from(Adventure adventure) {
        return new AdventureDto(adventure.id(), adventure.chronicleId(), adventure.name(), adventure.createdAt());
    }
}
''')
write('web/dto/CreateAdventureRequest.java', '''
package de.urr4.rp.roleplayer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateAdventureRequest(@NotBlank String name) {
}
''')
write('web/dto/CharacterDto.java', '''
package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Character;

import java.time.Instant;

public record CharacterDto(String id, String chronicleId, String name, String playerId, boolean hasSheet, Instant createdAt) {
    public static CharacterDto from(Character character) {
        return new CharacterDto(character.id(), character.chronicleId(), character.name(), character.playerId(),
                character.pdfObjectKey() != null && !character.pdfObjectKey().isBlank(), character.createdAt());
    }
}
''')
write('web/dto/ImportCharacterRequest.java', '''
package de.urr4.rp.roleplayer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ImportCharacterRequest(@NotBlank String characterId) {
}
''')
write('web/dto/AssignCharacterRequest.java', '''
package de.urr4.rp.roleplayer.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignCharacterRequest(@NotBlank String playerId, @NotBlank String characterId) {
}
''')
write('web/dto/CharacterAssignmentDto.java', '''
package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.CharacterAssignment;

import java.time.Instant;

public record CharacterAssignmentDto(String id, String adventureId, String playerId, String characterId,
                                     Instant startedAt, Instant endedAt) {
    public static CharacterAssignmentDto from(CharacterAssignment assignment) {
        return new CharacterAssignmentDto(assignment.id(), assignment.adventureId(), assignment.playerId(),
                assignment.characterId(), assignment.startedAt(), assignment.endedAt());
    }
}
''')
write('web/dto/RecordingDto.java', '''
package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Recording;

import java.time.Instant;

public record RecordingDto(String id, String chronicleId, String source, String status, Instant startedAt,
                           Instant endedAt, String audioObjectKey, String transcriptObjectKey) {
    public static RecordingDto from(Recording recording) {
        return new RecordingDto(recording.id(), recording.chronicleId(), recording.source().name(),
                recording.status().name(), recording.startedAt(), recording.endedAt(), recording.audioObjectKey(),
                recording.transcriptObjectKey());
    }
}
''')
write('web/ChronicleController.java', '''
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
''')
write('web/AdventureController.java', '''
package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.AdventureService;
import de.urr4.rp.roleplayer.web.dto.AdventureDto;
import de.urr4.rp.roleplayer.web.dto.CreateAdventureRequest;
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
public class AdventureController {

    private final AdventureService adventureService;

    public AdventureController(AdventureService adventureService) {
        this.adventureService = adventureService;
    }

    @GetMapping("/api/chronicles/{chronicleId}/adventures")
    public List<AdventureDto> listByChronicle(@PathVariable String chronicleId) {
        return adventureService.listByChronicle(chronicleId).stream().map(AdventureDto::from).toList();
    }

    @PostMapping("/api/chronicles/{chronicleId}/adventures")
    public AdventureDto create(@PathVariable String chronicleId, @Valid @RequestBody CreateAdventureRequest request) {
        return AdventureDto.from(adventureService.createAdventure(chronicleId, request.name()));
    }

    @GetMapping("/api/adventures/{id}")
    public ResponseEntity<AdventureDto> get(@PathVariable String id) {
        return adventureService.getAdventure(id)
                .map(AdventureDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
''')
write('web/CharacterController.java', '''
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
''')
write('web/CharacterAssignmentController.java', '''
package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.CharacterAssignmentService;
import de.urr4.rp.roleplayer.web.dto.AssignCharacterRequest;
import de.urr4.rp.roleplayer.web.dto.CharacterAssignmentDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/adventures/{adventureId}/assignments")
public class CharacterAssignmentController {

    private final CharacterAssignmentService characterAssignmentService;

    public CharacterAssignmentController(CharacterAssignmentService characterAssignmentService) {
        this.characterAssignmentService = characterAssignmentService;
    }

    @GetMapping
    public List<CharacterAssignmentDto> list(@PathVariable String adventureId,
                                             @RequestParam(defaultValue = "false") boolean activeOnly) {
        List<?> assignments = activeOnly
                ? characterAssignmentService.listActiveForAdventure(adventureId)
                : characterAssignmentService.listForAdventure(adventureId);
        return ((List<de.urr4.rp.roleplayer.domain.model.CharacterAssignment>) assignments).stream()
                .map(CharacterAssignmentDto::from)
                .toList();
    }

    @PostMapping
    public CharacterAssignmentDto assign(@PathVariable String adventureId,
                                         @Valid @RequestBody AssignCharacterRequest request) {
        return CharacterAssignmentDto.from(characterAssignmentService.assign(adventureId, request.playerId(),
                request.characterId()));
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> end(@PathVariable String assignmentId) {
        characterAssignmentService.endAssignment(assignmentId);
        return ResponseEntity.noContent().build();
    }
}
''')
write('web/ChronicleNpcController.java', '''
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
''')
write('web/ChronicleTranscriptController.java', '''
package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.RecordingService;
import de.urr4.rp.roleplayer.application.TranscriptEventPublisher;
import de.urr4.rp.roleplayer.web.dto.TranscriptSegmentDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chronicles/{chronicleId}/transcript")
public class ChronicleTranscriptController {

    private final RecordingService recordingService;
    private final TranscriptEventPublisher transcriptEventPublisher;

    public ChronicleTranscriptController(RecordingService recordingService,
                                         TranscriptEventPublisher transcriptEventPublisher) {
        this.recordingService = recordingService;
        this.transcriptEventPublisher = transcriptEventPublisher;
    }

    @GetMapping
    public List<TranscriptSegmentDto> transcript(@PathVariable String chronicleId) {
        return recordingService.getChronicleTranscript(chronicleId).stream().map(TranscriptSegmentDto::from).toList();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String chronicleId) {
        return transcriptEventPublisher.subscribe(chronicleId);
    }
}
''')
