package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.port.out.SessionCharacterLinkRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link SessionCharacterLinkRepository}, active only
 * in the {@code local} profile. Data is lost on restart.
 */
@Component
@Profile("local")
public class InMemorySessionCharacterLinkRepository implements SessionCharacterLinkRepository {

    private final Map<String, Set<String>> linksBySession = new ConcurrentHashMap<>();

    @Override
    public void link(String sessionId, String characterId) {
        linksBySession.computeIfAbsent(sessionId, s -> ConcurrentHashMap.newKeySet()).add(characterId);
    }

    @Override
    public void unlink(String sessionId, String characterId) {
        linksBySession.getOrDefault(sessionId, Set.of()).remove(characterId);
    }

    @Override
    public List<String> findCharacterIdsBySession(String sessionId) {
        return List.copyOf(linksBySession.getOrDefault(sessionId, Set.of()));
    }
}
