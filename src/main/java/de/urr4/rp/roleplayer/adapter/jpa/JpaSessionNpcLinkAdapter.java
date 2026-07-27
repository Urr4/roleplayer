package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.port.out.SessionNpcLinkRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!local")
public class JpaSessionNpcLinkAdapter implements SessionNpcLinkRepository {

    private final SpringDataSessionNpcRepository repository;

    public JpaSessionNpcLinkAdapter(SpringDataSessionNpcRepository repository) {
        this.repository = repository;
    }

    @Override
    public void link(String sessionId, String npcId) {
        repository.findBySessionIdAndNpcId(sessionId, npcId)
                .orElseGet(() -> repository.save(new SessionNpcEntity(sessionId, npcId)));
    }

    @Override
    public void unlink(String sessionId, String npcId) {
        repository.findBySessionIdAndNpcId(sessionId, npcId).ifPresent(repository::delete);
    }

    @Override
    public List<String> findNpcIdsBySession(String sessionId) {
        return repository.findBySessionId(sessionId).stream()
                .map(SessionNpcEntity::getNpcId)
                .toList();
    }
}
