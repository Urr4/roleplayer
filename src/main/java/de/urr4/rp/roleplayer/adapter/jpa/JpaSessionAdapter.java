package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.Session;
import de.urr4.rp.roleplayer.domain.port.out.SessionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaSessionAdapter implements SessionRepository {

    private final SpringDataSessionRepository repository;

    public JpaSessionAdapter(SpringDataSessionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Session save(Session session) {
        SessionEntity saved = repository.save(new SessionEntity(session.id(), session.name(), session.createdAt()));
        return toDomain(saved);
    }

    @Override
    public List<Session> findAll() {
        return repository.findAll().stream().map(JpaSessionAdapter::toDomain).toList();
    }

    @Override
    public Optional<Session> findById(String id) {
        return repository.findById(id).map(JpaSessionAdapter::toDomain);
    }

    private static Session toDomain(SessionEntity entity) {
        return new Session(entity.getId(), entity.getName(), entity.getCreatedAt());
    }
}
