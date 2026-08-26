package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.port.out.ChronicleNpcLinkRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryChronicleNpcLinkRepository implements ChronicleNpcLinkRepository {
    private final Map<String, Set<String>> linksByChronicle = new ConcurrentHashMap<>();
    @Override public void link(String chronicleId, String npcId) { linksByChronicle.computeIfAbsent(chronicleId, s -> ConcurrentHashMap.newKeySet()).add(npcId); }
    @Override public void unlink(String chronicleId, String npcId) { linksByChronicle.getOrDefault(chronicleId, Set.of()).remove(npcId); }
    @Override public List<String> findNpcIdsByChronicle(String chronicleId) { return List.copyOf(linksByChronicle.getOrDefault(chronicleId, Set.of())); }
}
