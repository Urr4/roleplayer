package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataSessionNpcRepository extends JpaRepository<SessionNpcEntity, Long> {
    List<SessionNpcEntity> findByChronicleId(String chronicleId);

    Optional<SessionNpcEntity> findByChronicleIdAndNpcId(String chronicleId, String npcId);
}
