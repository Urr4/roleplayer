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

for rel in [
    'domain/port/out/SessionCharacterLinkRepository.java',
    'adapter/jpa/JpaSessionCharacterLinkAdapter.java',
    'adapter/memory/InMemorySessionCharacterLinkRepository.java',
    'adapter/jpa/SessionCharacterEntity.java',
    'adapter/jpa/SpringDataSessionCharacterRepository.java',
    'web/SessionCharacterController.java',
]:
    delete(rel)

write('adapter/jpa/ChronicleEntity.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chronicles")
public class ChronicleEntity {

    @Id
    private String id;
    private String name;
    private Instant createdAt;

    protected ChronicleEntity() {
    }

    public ChronicleEntity(String id, String name, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
''')
write('adapter/jpa/SpringDataChronicleRepository.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataChronicleRepository extends JpaRepository<ChronicleEntity, String> {
}
''')
write('adapter/jpa/JpaChronicleAdapter.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaChronicleAdapter implements ChronicleRepository {

    private final SpringDataChronicleRepository repository;

    public JpaChronicleAdapter(SpringDataChronicleRepository repository) {
        this.repository = repository;
    }

    @Override
    public Chronicle save(Chronicle chronicle) {
        ChronicleEntity saved = repository.save(new ChronicleEntity(chronicle.id(), chronicle.name(), chronicle.createdAt()));
        return toDomain(saved);
    }

    @Override
    public List<Chronicle> findAll() {
        return repository.findAll().stream().map(JpaChronicleAdapter::toDomain).toList();
    }

    @Override
    public Optional<Chronicle> findById(String id) {
        return repository.findById(id).map(JpaChronicleAdapter::toDomain);
    }

    private static Chronicle toDomain(ChronicleEntity entity) {
        return new Chronicle(entity.getId(), entity.getName(), entity.getCreatedAt());
    }
}
''')
write('adapter/memory/InMemoryChronicleRepository.java', '''
package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryChronicleRepository implements ChronicleRepository {

    private final Map<String, Chronicle> store = new ConcurrentHashMap<>();

    @Override
    public Chronicle save(Chronicle chronicle) {
        store.put(chronicle.id(), chronicle);
        return chronicle;
    }

    @Override
    public List<Chronicle> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public Optional<Chronicle> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
''')
write('adapter/jpa/AdventureEntity.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "adventures")
public class AdventureEntity {
    @Id
    private String id;
    private String chronicleId;
    private String name;
    private Instant createdAt;

    protected AdventureEntity() {}

    public AdventureEntity(String id, String chronicleId, String name, Instant createdAt) {
        this.id = id;
        this.chronicleId = chronicleId;
        this.name = name;
        this.createdAt = createdAt;
    }
    public String getId() { return id; }
    public String getChronicleId() { return chronicleId; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
''')
write('adapter/jpa/SpringDataAdventureRepository.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataAdventureRepository extends JpaRepository<AdventureEntity, String> {
    List<AdventureEntity> findByChronicleId(String chronicleId);
}
''')
write('adapter/jpa/JpaAdventureAdapter.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaAdventureAdapter implements AdventureRepository {
    private final SpringDataAdventureRepository repository;
    public JpaAdventureAdapter(SpringDataAdventureRepository repository) { this.repository = repository; }
    @Override
    public Adventure save(Adventure adventure) {
        AdventureEntity saved = repository.save(new AdventureEntity(adventure.id(), adventure.chronicleId(), adventure.name(), adventure.createdAt()));
        return toDomain(saved);
    }
    @Override
    public List<Adventure> findAll() { return repository.findAll().stream().map(JpaAdventureAdapter::toDomain).toList(); }
    @Override
    public List<Adventure> findByChronicleId(String chronicleId) { return repository.findByChronicleId(chronicleId).stream().map(JpaAdventureAdapter::toDomain).toList(); }
    @Override
    public Optional<Adventure> findById(String id) { return repository.findById(id).map(JpaAdventureAdapter::toDomain); }
    private static Adventure toDomain(AdventureEntity entity) { return new Adventure(entity.getId(), entity.getChronicleId(), entity.getName(), entity.getCreatedAt()); }
}
''')
write('adapter/memory/InMemoryAdventureRepository.java', '''
package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryAdventureRepository implements AdventureRepository {
    private final Map<String, Adventure> store = new ConcurrentHashMap<>();
    @Override public Adventure save(Adventure adventure) { store.put(adventure.id(), adventure); return adventure; }
    @Override public List<Adventure> findAll() { return List.copyOf(store.values()); }
    @Override public List<Adventure> findByChronicleId(String chronicleId) { return store.values().stream().filter(a -> a.chronicleId().equals(chronicleId)).toList(); }
    @Override public Optional<Adventure> findById(String id) { return Optional.ofNullable(store.get(id)); }
}
''')
write('adapter/jpa/CharacterAssignmentEntity.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "character_assignments")
public class CharacterAssignmentEntity {
    @Id
    private String id;
    private String adventureId;
    private String playerId;
    private String characterId;
    private Instant startedAt;
    private Instant endedAt;
    protected CharacterAssignmentEntity() {}
    public CharacterAssignmentEntity(String id, String adventureId, String playerId, String characterId, Instant startedAt, Instant endedAt) {
        this.id=id; this.adventureId=adventureId; this.playerId=playerId; this.characterId=characterId; this.startedAt=startedAt; this.endedAt=endedAt;
    }
    public String getId() { return id; }
    public String getAdventureId() { return adventureId; }
    public String getPlayerId() { return playerId; }
    public String getCharacterId() { return characterId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
}
''')
write('adapter/jpa/SpringDataCharacterAssignmentRepository.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataCharacterAssignmentRepository extends JpaRepository<CharacterAssignmentEntity, String> {
    List<CharacterAssignmentEntity> findByAdventureIdOrderByStartedAtAsc(String adventureId);
    List<CharacterAssignmentEntity> findByAdventureIdAndEndedAtIsNullOrderByStartedAtAsc(String adventureId);
    Optional<CharacterAssignmentEntity> findFirstByAdventureIdAndPlayerIdAndEndedAtIsNull(String adventureId, String playerId);
}
''')
write('adapter/jpa/JpaCharacterAssignmentAdapter.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.CharacterAssignment;
import de.urr4.rp.roleplayer.domain.port.out.CharacterAssignmentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaCharacterAssignmentAdapter implements CharacterAssignmentRepository {
    private final SpringDataCharacterAssignmentRepository repository;
    public JpaCharacterAssignmentAdapter(SpringDataCharacterAssignmentRepository repository) { this.repository = repository; }
    @Override public CharacterAssignment save(CharacterAssignment assignment) {
        CharacterAssignmentEntity saved = repository.save(new CharacterAssignmentEntity(assignment.id(), assignment.adventureId(), assignment.playerId(), assignment.characterId(), assignment.startedAt(), assignment.endedAt()));
        return toDomain(saved);
    }
    @Override public Optional<CharacterAssignment> findById(String id) { return repository.findById(id).map(JpaCharacterAssignmentAdapter::toDomain); }
    @Override public List<CharacterAssignment> findByAdventureId(String adventureId) { return repository.findByAdventureIdOrderByStartedAtAsc(adventureId).stream().map(JpaCharacterAssignmentAdapter::toDomain).toList(); }
    @Override public List<CharacterAssignment> findByAdventureIdAndEndedAtIsNull(String adventureId) { return repository.findByAdventureIdAndEndedAtIsNullOrderByStartedAtAsc(adventureId).stream().map(JpaCharacterAssignmentAdapter::toDomain).toList(); }
    @Override public Optional<CharacterAssignment> findActiveByAdventureIdAndPlayerId(String adventureId, String playerId) { return repository.findFirstByAdventureIdAndPlayerIdAndEndedAtIsNull(adventureId, playerId).map(JpaCharacterAssignmentAdapter::toDomain); }
    private static CharacterAssignment toDomain(CharacterAssignmentEntity entity) { return new CharacterAssignment(entity.getId(), entity.getAdventureId(), entity.getPlayerId(), entity.getCharacterId(), entity.getStartedAt(), entity.getEndedAt()); }
}
''')
write('adapter/memory/InMemoryCharacterAssignmentRepository.java', '''
package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.CharacterAssignment;
import de.urr4.rp.roleplayer.domain.port.out.CharacterAssignmentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryCharacterAssignmentRepository implements CharacterAssignmentRepository {
    private final Map<String, CharacterAssignment> store = new ConcurrentHashMap<>();
    @Override public CharacterAssignment save(CharacterAssignment assignment) { store.put(assignment.id(), assignment); return assignment; }
    @Override public Optional<CharacterAssignment> findById(String id) { return Optional.ofNullable(store.get(id)); }
    @Override public List<CharacterAssignment> findByAdventureId(String adventureId) { return store.values().stream().filter(a -> a.adventureId().equals(adventureId)).sorted(Comparator.comparing(CharacterAssignment::startedAt)).toList(); }
    @Override public List<CharacterAssignment> findByAdventureIdAndEndedAtIsNull(String adventureId) { return store.values().stream().filter(a -> a.adventureId().equals(adventureId) && a.endedAt() == null).sorted(Comparator.comparing(CharacterAssignment::startedAt)).toList(); }
    @Override public Optional<CharacterAssignment> findActiveByAdventureIdAndPlayerId(String adventureId, String playerId) { return store.values().stream().filter(a -> a.adventureId().equals(adventureId) && a.playerId().equals(playerId) && a.endedAt() == null).findFirst(); }
}
''')
write('adapter/jpa/CharacterEntity.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "characters")
public class CharacterEntity {

    @Id
    private String id;
    private String chronicleId;
    private String name;
    private String playerId;
    private String pdfObjectKey;
    private Instant createdAt;

    protected CharacterEntity() {}

    public CharacterEntity(String id, String chronicleId, String name, String playerId, String pdfObjectKey, Instant createdAt) {
        this.id = id; this.chronicleId = chronicleId; this.name = name; this.playerId = playerId; this.pdfObjectKey = pdfObjectKey; this.createdAt = createdAt;
    }
    public String getId() { return id; }
    public String getChronicleId() { return chronicleId; }
    public String getName() { return name; }
    public String getPlayerId() { return playerId; }
    public String getPdfObjectKey() { return pdfObjectKey; }
    public Instant getCreatedAt() { return createdAt; }
}
''')
write('adapter/jpa/SpringDataCharacterRepository.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataCharacterRepository extends JpaRepository<CharacterEntity, String> {
    List<CharacterEntity> findByChronicleId(String chronicleId);
}
''')
write('adapter/jpa/JpaCharacterAdapter.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.Character;
import de.urr4.rp.roleplayer.domain.port.out.CharacterRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaCharacterAdapter implements CharacterRepository {
    private final SpringDataCharacterRepository repository;
    public JpaCharacterAdapter(SpringDataCharacterRepository repository) { this.repository = repository; }
    @Override public Character save(Character character) {
        CharacterEntity saved = repository.save(new CharacterEntity(character.id(), character.chronicleId(), character.name(), character.playerId(), character.pdfObjectKey(), character.createdAt()));
        return toDomain(saved);
    }
    @Override public List<Character> findAll() { return repository.findAll().stream().map(JpaCharacterAdapter::toDomain).toList(); }
    @Override public List<Character> findByChronicleId(String chronicleId) { return repository.findByChronicleId(chronicleId).stream().map(JpaCharacterAdapter::toDomain).toList(); }
    @Override public Optional<Character> findById(String id) { return repository.findById(id).map(JpaCharacterAdapter::toDomain); }
    private static Character toDomain(CharacterEntity entity) { return new Character(entity.getId(), entity.getChronicleId(), entity.getName(), entity.getPlayerId(), entity.getPdfObjectKey(), entity.getCreatedAt()); }
}
''')
write('adapter/memory/InMemoryCharacterRepository.java', '''
package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Character;
import de.urr4.rp.roleplayer.domain.port.out.CharacterRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryCharacterRepository implements CharacterRepository {
    private final Map<String, Character> store = new ConcurrentHashMap<>();
    @Override public Character save(Character character) { store.put(character.id(), character); return character; }
    @Override public List<Character> findAll() { return List.copyOf(store.values()); }
    @Override public List<Character> findByChronicleId(String chronicleId) { return store.values().stream().filter(c -> c.chronicleId().equals(chronicleId)).toList(); }
    @Override public Optional<Character> findById(String id) { return Optional.ofNullable(store.get(id)); }
}
''')
write('adapter/jpa/ChronicleNpcEntity.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "chronicle_npcs", uniqueConstraints = @UniqueConstraint(columnNames = {"chronicle_id", "npc_id"}))
public class ChronicleNpcEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "chronicle_id")
    private String chronicleId;
    @Column(name = "npc_id")
    private String npcId;
    protected ChronicleNpcEntity() {}
    public ChronicleNpcEntity(String chronicleId, String npcId) { this.chronicleId = chronicleId; this.npcId = npcId; }
    public Long getId() { return id; }
    public String getChronicleId() { return chronicleId; }
    public String getNpcId() { return npcId; }
}
''')
write('adapter/jpa/SpringDataChronicleNpcRepository.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataChronicleNpcRepository extends JpaRepository<ChronicleNpcEntity, Long> {
    List<ChronicleNpcEntity> findByChronicleId(String chronicleId);
    Optional<ChronicleNpcEntity> findByChronicleIdAndNpcId(String chronicleId, String npcId);
}
''')
write('adapter/jpa/JpaChronicleNpcLinkAdapter.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.port.out.ChronicleNpcLinkRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!local")
public class JpaChronicleNpcLinkAdapter implements ChronicleNpcLinkRepository {
    private final SpringDataChronicleNpcRepository repository;
    public JpaChronicleNpcLinkAdapter(SpringDataChronicleNpcRepository repository) { this.repository = repository; }
    @Override public void link(String chronicleId, String npcId) { repository.findByChronicleIdAndNpcId(chronicleId, npcId).orElseGet(() -> repository.save(new ChronicleNpcEntity(chronicleId, npcId))); }
    @Override public void unlink(String chronicleId, String npcId) { repository.findByChronicleIdAndNpcId(chronicleId, npcId).ifPresent(repository::delete); }
    @Override public List<String> findNpcIdsByChronicle(String chronicleId) { return repository.findByChronicleId(chronicleId).stream().map(ChronicleNpcEntity::getNpcId).toList(); }
}
''')
write('adapter/memory/InMemoryChronicleNpcLinkRepository.java', '''
package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.port.out.ChronicleNpcLinkRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryChronicleNpcLinkRepository implements ChronicleNpcLinkRepository {
    private final Map<String, Set<String>> linksByChronicle = new ConcurrentHashMap<>();
    @Override public void link(String chronicleId, String npcId) { linksByChronicle.computeIfAbsent(chronicleId, s -> ConcurrentHashMap.newKeySet()).add(npcId); }
    @Override public void unlink(String chronicleId, String npcId) { linksByChronicle.getOrDefault(chronicleId, Set.of()).remove(npcId); }
    @Override public List<String> findNpcIdsByChronicle(String chronicleId) { return List.copyOf(linksByChronicle.getOrDefault(chronicleId, Set.of())); }
}
''')
write('adapter/jpa/RecordingEntity.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "recordings")
public class RecordingEntity {
    @Id private String id;
    private String chronicleId;
    @Enumerated(EnumType.STRING) private RecordingSource source;
    @Enumerated(EnumType.STRING) private RecordingStatus status;
    private Instant startedAt;
    private Instant endedAt;
    private String audioObjectKey;
    private String transcriptObjectKey;
    protected RecordingEntity() {}
    public RecordingEntity(String id, String chronicleId, RecordingSource source, RecordingStatus status, Instant startedAt, Instant endedAt, String audioObjectKey, String transcriptObjectKey) {
        this.id=id; this.chronicleId=chronicleId; this.source=source; this.status=status; this.startedAt=startedAt; this.endedAt=endedAt; this.audioObjectKey=audioObjectKey; this.transcriptObjectKey=transcriptObjectKey;
    }
    public String getId() { return id; }
    public String getChronicleId() { return chronicleId; }
    public RecordingSource getSource() { return source; }
    public RecordingStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public String getAudioObjectKey() { return audioObjectKey; }
    public String getTranscriptObjectKey() { return transcriptObjectKey; }
}
''')
write('adapter/jpa/SpringDataRecordingRepository.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataRecordingRepository extends JpaRepository<RecordingEntity, String> {
    List<RecordingEntity> findByChronicleId(String chronicleId);
}
''')
write('adapter/jpa/JpaRecordingAdapter.java', '''
package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaRecordingAdapter implements RecordingRepository {
    private final SpringDataRecordingRepository repository;
    public JpaRecordingAdapter(SpringDataRecordingRepository repository) { this.repository = repository; }
    @Override public Recording save(Recording recording) {
        RecordingEntity saved = repository.save(new RecordingEntity(recording.id(), recording.chronicleId(), recording.source(), recording.status(), recording.startedAt(), recording.endedAt(), recording.audioObjectKey(), recording.transcriptObjectKey()));
        return toDomain(saved);
    }
    @Override public List<Recording> findByChronicleId(String chronicleId) { return repository.findByChronicleId(chronicleId).stream().map(JpaRecordingAdapter::toDomain).toList(); }
    @Override public Optional<Recording> findById(String id) { return repository.findById(id).map(JpaRecordingAdapter::toDomain); }
    private static Recording toDomain(RecordingEntity entity) { return new Recording(entity.getId(), entity.getChronicleId(), entity.getSource(), entity.getStatus(), entity.getStartedAt(), entity.getEndedAt(), entity.getAudioObjectKey(), entity.getTranscriptObjectKey()); }
}
''')
write('adapter/memory/InMemoryRecordingRepository.java', '''
package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryRecordingRepository implements RecordingRepository {
    private final Map<String, Recording> store = new ConcurrentHashMap<>();
    @Override public Recording save(Recording recording) { store.put(recording.id(), recording); return recording; }
    @Override public List<Recording> findByChronicleId(String chronicleId) { return store.values().stream().filter(recording -> recording.chronicleId().equals(chronicleId)).toList(); }
    @Override public Optional<Recording> findById(String id) { return Optional.ofNullable(store.get(id)); }
}
''')
