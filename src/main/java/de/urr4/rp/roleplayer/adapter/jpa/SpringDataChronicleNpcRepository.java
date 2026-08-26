package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataChronicleNpcRepository extends JpaRepository<ChronicleNpcEntity, Long> {
    List<ChronicleNpcEntity> findByChronicleId(String chronicleId);
    Optional<ChronicleNpcEntity> findByChronicleIdAndNpcId(String chronicleId, String npcId);
}
