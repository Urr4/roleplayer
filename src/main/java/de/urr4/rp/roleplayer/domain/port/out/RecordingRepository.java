package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Recording;

import java.util.List;
import java.util.Optional;

public interface RecordingRepository {
    Recording save(Recording recording);

    List<Recording> findByChronicleId(String chronicleId);

    List<Recording> findByAdventureId(String adventureId);

    Optional<Recording> findById(String id);
}
