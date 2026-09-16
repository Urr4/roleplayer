package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Npc;
import de.urr4.rp.roleplayer.domain.model.NpcGeneration;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleNpcLinkRepository;
import de.urr4.rp.roleplayer.domain.port.out.NpcGeneratorClient;
import de.urr4.rp.roleplayer.domain.port.out.NpcRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NpcService {

    private final NpcRepository npcRepository;
    private final ChronicleNpcLinkRepository linkRepository;
    private final NpcGeneratorClient npcGeneratorClient;

    public NpcService(NpcRepository npcRepository, ChronicleNpcLinkRepository linkRepository,
                       NpcGeneratorClient npcGeneratorClient) {
        this.npcRepository = npcRepository;
        this.linkRepository = linkRepository;
        this.npcGeneratorClient = npcGeneratorClient;
    }

    /** Generates a brand-new, unsaved NPC sheet from a short description — used by the "Generate with AI" button. */
    public Npc generateNpc(String name, String shortDescription) {
        NpcGeneration generation = npcGeneratorClient.generate(shortDescription);
        return new Npc(null, name == null || name.isBlank() ? "Unnamed Stranger" : name,
                generation.firstImpression(), generation.goal(), generation.attitude(),
                generation.rulesAndTaboos(), generation.quirks(), null, null);
    }

    public Npc saveNpcInChronicle(String chronicleId, String name, String firstImpression, String goal,
                                  String attitude, String rulesAndTaboos, String quirks) {
        Npc npc = new Npc(UUID.randomUUID().toString(), name, firstImpression, goal, attitude, rulesAndTaboos, quirks,
                chronicleId, Instant.now());
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
