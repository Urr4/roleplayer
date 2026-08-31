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
    @Override public void deleteById(String id) { repository.deleteById(id); }
    private static Character toDomain(CharacterEntity entity) { return new Character(entity.getId(), entity.getChronicleId(), entity.getName(), entity.getPlayerId(), entity.getPdfObjectKey(), entity.getCreatedAt()); }
}
