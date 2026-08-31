package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.port.out.ChronicleNpcLinkRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!local")
public class JpaChronicleNpcLinkAdapter implements ChronicleNpcLinkRepository {
    private final SpringDataChronicleNpcRepository repository;
    public JpaChronicleNpcLinkAdapter(SpringDataChronicleNpcRepository repository) { this.repository = repository; }
    @Override public void link(String chronicleId, String npcId) { repository.findByChronicleIdAndNpcId(chronicleId, npcId).orElseGet(() -> repository.save(new ChronicleNpcEntity(chronicleId, npcId))); }
    @Override public void unlink(String chronicleId, String npcId) { repository.findByChronicleIdAndNpcId(chronicleId, npcId).ifPresent(repository::delete); }
    @Override public List<String> findNpcIdsByChronicle(String chronicleId) { return repository.findByChronicleId(chronicleId).stream().map(ChronicleNpcEntity::getNpcId).toList(); }
    @Override public void unlinkAll(String chronicleId) { repository.deleteByChronicleId(chronicleId); }
}
