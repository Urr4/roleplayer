package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Npc;

import java.util.List;
import java.util.Optional;

public interface NpcRepository {
    Npc save(Npc npc);

    List<Npc> findAll();

    List<Npc> findByIds(List<String> ids);

    Optional<Npc> findById(String id);
}
