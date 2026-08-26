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
    public void link(String chronicleId, String npcId) {
        repository.findByChronicleIdAndNpcId(chronicleId, npcId)
                .orElseGet(() -> repository.save(new SessionNpcEntity(chronicleId, npcId)));
    }

    @Override
    public void unlink(String chronicleId, String npcId) {
        repository.findByChronicleIdAndNpcId(chronicleId, npcId).ifPresent(repository::delete);
    }

    @Override
    public List<String> findNpcIdsByChronicle(String chronicleId) {
        return repository.findByChronicleId(chronicleId).stream()
                .map(SessionNpcEntity::getNpcId)
                .toList();
    }
}
