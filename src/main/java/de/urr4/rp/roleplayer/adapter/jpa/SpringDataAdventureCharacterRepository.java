package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataAdventureCharacterRepository extends JpaRepository<AdventureCharacterEntity, String> {
    List<AdventureCharacterEntity> findByAdventureIdOrderByAddedAtAsc(String adventureId);
    Optional<AdventureCharacterEntity> findByAdventureIdAndCharacterId(String adventureId, String characterId);
    void deleteByAdventureId(String adventureId);
    void deleteByCharacterId(String characterId);
}
