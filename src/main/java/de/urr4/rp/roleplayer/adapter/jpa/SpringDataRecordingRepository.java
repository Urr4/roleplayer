package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SpringDataRecordingRepository extends JpaRepository<RecordingEntity, String> {
    List<RecordingEntity> findByChronicleId(String chronicleId);
    List<RecordingEntity> findByAdventureId(String adventureId);
    List<RecordingEntity> findByStatusIn(Collection<de.urr4.rp.roleplayer.domain.model.RecordingStatus> statuses);
}
