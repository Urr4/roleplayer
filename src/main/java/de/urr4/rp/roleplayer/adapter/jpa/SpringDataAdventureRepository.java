package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.AdventureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataAdventureRepository extends JpaRepository<AdventureEntity, String> {
    List<AdventureEntity> findByChronicleId(String chronicleId);
    Optional<AdventureEntity> findByChronicleIdAndStatus(String chronicleId, AdventureStatus status);
}
