package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.AdventureCharacter;

import java.util.List;
import java.util.Optional;

public interface AdventureCharacterRepository {
    AdventureCharacter save(AdventureCharacter adventureCharacter);

    List<AdventureCharacter> findByAdventureId(String adventureId);

    Optional<AdventureCharacter> findByAdventureIdAndCharacterId(String adventureId, String characterId);

    void deleteById(String id);
}
