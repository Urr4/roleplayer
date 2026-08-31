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

    public JpaAdventureAdapter(SpringDataAdventureRepository repository) {
        this.repository = repository;
    }

    @Override
    public Adventure save(Adventure adventure) {
        AdventureEntity saved = repository.save(new AdventureEntity(
                adventure.id(), adventure.chronicleId(), adventure.name(), adventure.status(),
                adventure.createdAt(), adventure.startedAt(), adventure.endedAt()));
        return toDomain(saved);
    }

    @Override
    public List<Adventure> findAll() {
        return repository.findAll().stream().map(JpaAdventureAdapter::toDomain).toList();
    }

    @Override
    public List<Adventure> findByChronicleId(String chronicleId) {
        return repository.findByChronicleId(chronicleId).stream().map(JpaAdventureAdapter::toDomain).toList();
    }

    @Override
    public Optional<Adventure> findById(String id) {
        return repository.findById(id).map(JpaAdventureAdapter::toDomain);
    }

    @Override
    public Optional<Adventure> findActiveByChronicleId(String chronicleId) {
        return repository.findByChronicleIdAndStatus(chronicleId, de.urr4.rp.roleplayer.domain.model.AdventureStatus.ACTIVE)
                .map(JpaAdventureAdapter::toDomain);
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }

    private static Adventure toDomain(AdventureEntity entity) {
        return new Adventure(entity.getId(), entity.getChronicleId(), entity.getName(), entity.getStatus(),
                entity.getCreatedAt(), entity.getStartedAt(), entity.getEndedAt());
    }
}
