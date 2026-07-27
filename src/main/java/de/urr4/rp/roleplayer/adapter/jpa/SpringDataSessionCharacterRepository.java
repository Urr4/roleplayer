package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataSessionCharacterRepository extends JpaRepository<SessionCharacterEntity, Long> {
    List<SessionCharacterEntity> findBySessionId(String sessionId);

    Optional<SessionCharacterEntity> findBySessionIdAndCharacterId(String sessionId, String characterId);
}
