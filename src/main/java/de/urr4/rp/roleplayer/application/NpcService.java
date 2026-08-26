package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Npc;
import de.urr4.rp.roleplayer.domain.model.NpcAttributePools;
import de.urr4.rp.roleplayer.domain.model.NpcStatus;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleNpcLinkRepository;
import de.urr4.rp.roleplayer.domain.port.out.NpcRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NpcService {

    private final NpcRepository npcRepository;
    private final ChronicleNpcLinkRepository linkRepository;

    public NpcService(NpcRepository npcRepository, ChronicleNpcLinkRepository linkRepository) {
        this.npcRepository = npcRepository;
        this.linkRepository = linkRepository;
    }

    public Npc rollRandomNpc(String name) {
        return new Npc(null, name == null || name.isBlank() ? "Unnamed Stranger" : name,
                NpcAttributePools.randomMotive(), NpcAttributePools.randomStatus(), NpcAttributePools.randomMood(),
                null, null);
    }

    public String randomMotive() { return NpcAttributePools.randomMotive(); }
    public String randomMood() { return NpcAttributePools.randomMood(); }
    public NpcStatus randomStatus() { return NpcAttributePools.randomStatus(); }

    public Npc saveNpcInChronicle(String chronicleId, String name, String motive, NpcStatus status, String mood) {
        Npc npc = new Npc(UUID.randomUUID().toString(), name, motive, status, mood, chronicleId, Instant.now());
        Npc saved = npcRepository.save(npc);
        linkRepository.link(chronicleId, saved.id());
        return saved;
    }

    public List<Npc> listChronicleNpcs(String chronicleId) {
        List<String> ids = linkRepository.findNpcIdsByChronicle(chronicleId);
        return npcRepository.findByIds(ids);
    }

    public List<Npc> listAllNpcs() { return npcRepository.findAll(); }

    public void importNpcIntoChronicle(String chronicleId, String npcId) { linkRepository.link(chronicleId, npcId); }

    public void removeNpcFromChronicle(String chronicleId, String npcId) { linkRepository.unlink(chronicleId, npcId); }
}
