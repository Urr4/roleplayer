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
