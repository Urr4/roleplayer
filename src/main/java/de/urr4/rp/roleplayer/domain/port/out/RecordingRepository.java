package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecordingRepository {
    Recording save(Recording recording);

    List<Recording> findByChronicleId(String chronicleId);

    List<Recording> findByAdventureId(String adventureId);

    Optional<Recording> findById(String id);

    List<Recording> findByStatusIn(Collection<RecordingStatus> statuses);

    void deleteById(String id);
}
