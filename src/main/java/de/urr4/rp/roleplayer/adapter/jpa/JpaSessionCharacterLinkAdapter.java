package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.port.out.SessionCharacterLinkRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!local")
public class JpaSessionCharacterLinkAdapter implements SessionCharacterLinkRepository {

    private final SpringDataSessionCharacterRepository repository;

    public JpaSessionCharacterLinkAdapter(SpringDataSessionCharacterRepository repository) {
        this.repository = repository;
    }

    @Override
    public void link(String sessionId, String characterId) {
        repository.findBySessionIdAndCharacterId(sessionId, characterId)
                .orElseGet(() -> repository.save(new SessionCharacterEntity(sessionId, characterId)));
    }

    @Override
    public void unlink(String sessionId, String characterId) {
        repository.findBySessionIdAndCharacterId(sessionId, characterId).ifPresent(repository::delete);
    }

    @Override
    public List<String> findCharacterIdsBySession(String sessionId) {
        return repository.findBySessionId(sessionId).stream()
                .map(SessionCharacterEntity::getCharacterId)
                .toList();
    }
}
