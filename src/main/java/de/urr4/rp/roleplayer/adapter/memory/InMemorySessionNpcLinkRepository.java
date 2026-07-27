package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.port.out.SessionNpcLinkRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link SessionNpcLinkRepository}, active only in the
 * {@code local} profile. Data is lost on restart.
 */
@Component
@Profile("local")
public class InMemorySessionNpcLinkRepository implements SessionNpcLinkRepository {

    private final Map<String, Set<String>> linksBySession = new ConcurrentHashMap<>();

    @Override
    public void link(String sessionId, String npcId) {
        linksBySession.computeIfAbsent(sessionId, s -> ConcurrentHashMap.newKeySet()).add(npcId);
    }

    @Override
    public void unlink(String sessionId, String npcId) {
        linksBySession.getOrDefault(sessionId, Set.of()).remove(npcId);
    }

    @Override
    public List<String> findNpcIdsBySession(String sessionId) {
        return List.copyOf(linksBySession.getOrDefault(sessionId, Set.of()));
    }
}
