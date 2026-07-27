package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Session;
import de.urr4.rp.roleplayer.domain.port.out.SessionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link SessionRepository}, active only in the
 * {@code local} profile so the app can run without SQLite for local dev/demo
 * purposes. Data is lost on restart.
 */
@Component
@Profile("local")
public class InMemorySessionRepository implements SessionRepository {

    private final Map<String, Session> store = new ConcurrentHashMap<>();

    @Override
    public Session save(Session session) {
        store.put(session.id(), session);
        return session;
    }

    @Override
    public List<Session> findAll() {
        return List.copyOf(store.values());
    }

    @Override
    public Optional<Session> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }
}
