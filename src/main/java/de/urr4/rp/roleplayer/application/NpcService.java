package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Npc;
import de.urr4.rp.roleplayer.domain.model.NpcAttributePools;
import de.urr4.rp.roleplayer.domain.model.NpcStatus;
import de.urr4.rp.roleplayer.domain.port.out.NpcRepository;
import de.urr4.rp.roleplayer.domain.port.out.SessionNpcLinkRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NpcService {

    private final NpcRepository npcRepository;
    private final SessionNpcLinkRepository linkRepository;

    public NpcService(NpcRepository npcRepository, SessionNpcLinkRepository linkRepository) {
        this.npcRepository = npcRepository;
        this.linkRepository = linkRepository;
    }

    /**
     * Rolls a brand-new, unsaved random NPC — used for the "Random NPC" button and
     * as the initial suggestion in the creation form.
     */
    public Npc rollRandomNpc(String name) {
        return new Npc(null, name == null || name.isBlank() ? "Unnamed Stranger" : name,
                NpcAttributePools.randomMotive(), NpcAttributePools.randomStatus(), NpcAttributePools.randomMood(),
                null, null);
    }

    public String randomMotive() {
        return NpcAttributePools.randomMotive();
    }

    public String randomMood() {
        return NpcAttributePools.randomMood();
    }

    public NpcStatus randomStatus() {
        return NpcAttributePools.randomStatus();
    }

    public Npc saveNpcInSession(String sessionId, String name, String motive, NpcStatus status, String mood) {
        Npc npc = new Npc(UUID.randomUUID().toString(), name, motive, status, mood, sessionId, Instant.now());
        Npc saved = npcRepository.save(npc);
        linkRepository.link(sessionId, saved.id());
        return saved;
    }

    public List<Npc> listSessionNpcs(String sessionId) {
        List<String> ids = linkRepository.findNpcIdsBySession(sessionId);
        return npcRepository.findByIds(ids);
    }

    public List<Npc> listAllNpcs() {
        return npcRepository.findAll();
    }

    public void importNpcIntoSession(String sessionId, String npcId) {
        linkRepository.link(sessionId, npcId);
    }

    public void removeNpcFromSession(String sessionId, String npcId) {
        linkRepository.unlink(sessionId, npcId);
    }
}
