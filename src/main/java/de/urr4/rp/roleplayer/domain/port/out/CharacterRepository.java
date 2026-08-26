package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Character;

import java.util.List;
import java.util.Optional;

public interface CharacterRepository {
    Character save(Character character);

    List<Character> findAll();

    List<Character> findByChronicleId(String chronicleId);

    Optional<Character> findById(String id);
}
