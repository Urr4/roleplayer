package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Adventure;

import java.util.List;
import java.util.Optional;

public interface AdventureRepository {
    Adventure save(Adventure adventure);

    List<Adventure> findAll();

    List<Adventure> findByChronicleId(String chronicleId);

    Optional<Adventure> findById(String id);

    Optional<Adventure> findActiveByChronicleId(String chronicleId);

    void deleteById(String id);
}
