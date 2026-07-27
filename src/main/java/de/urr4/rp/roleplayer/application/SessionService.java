package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Session;
import de.urr4.rp.roleplayer.domain.port.out.SessionRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public Session createSession(String name) {
        Session session = new Session(UUID.randomUUID().toString(), name, Instant.now());
        return sessionRepository.save(session);
    }

    public List<Session> listSessions() {
        return sessionRepository.findAll();
    }

    public Optional<Session> getSession(String id) {
        return sessionRepository.findById(id);
    }
}
