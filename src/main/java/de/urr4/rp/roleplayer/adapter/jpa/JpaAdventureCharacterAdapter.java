package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.AdventureCharacter;
import de.urr4.rp.roleplayer.domain.port.out.AdventureCharacterRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaAdventureCharacterAdapter implements AdventureCharacterRepository {
    private final SpringDataAdventureCharacterRepository repository;

    public JpaAdventureCharacterAdapter(SpringDataAdventureCharacterRepository repository) {
        this.repository = repository;
    }

    @Override
    public AdventureCharacter save(AdventureCharacter adventureCharacter) {
        AdventureCharacterEntity saved = repository.save(new AdventureCharacterEntity(
                adventureCharacter.id(), adventureCharacter.adventureId(), adventureCharacter.characterId(),
                adventureCharacter.addedAt()));
        return toDomain(saved);
    }

    @Override
    public List<AdventureCharacter> findByAdventureId(String adventureId) {
        return repository.findByAdventureIdOrderByAddedAtAsc(adventureId).stream()
                .map(JpaAdventureCharacterAdapter::toDomain).toList();
    }

    @Override
    public Optional<AdventureCharacter> findByAdventureIdAndCharacterId(String adventureId, String characterId) {
        return repository.findByAdventureIdAndCharacterId(adventureId, characterId).map(JpaAdventureCharacterAdapter::toDomain);
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteByAdventureId(String adventureId) {
        repository.deleteByAdventureId(adventureId);
    }

    @Override
    public void deleteByCharacterId(String characterId) {
        repository.deleteByCharacterId(characterId);
    }

    private static AdventureCharacter toDomain(AdventureCharacterEntity entity) {
        return new AdventureCharacter(entity.getId(), entity.getAdventureId(), entity.getCharacterId(), entity.getAddedAt());
    }
}
