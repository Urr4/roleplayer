package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@Profile("!local")
public class JpaRecordingAdapter implements RecordingRepository {
    private final SpringDataRecordingRepository repository;
    public JpaRecordingAdapter(SpringDataRecordingRepository repository) { this.repository = repository; }
    @Override public Recording save(Recording recording) {
        RecordingEntity saved = repository.save(new RecordingEntity(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(), recording.status(), recording.startedAt(), recording.endedAt(), recording.audioObjectKey(), recording.transcriptObjectKey()));
        return toDomain(saved);
    }
    @Override public List<Recording> findByChronicleId(String chronicleId) { return repository.findByChronicleId(chronicleId).stream().map(JpaRecordingAdapter::toDomain).toList(); }
    @Override public List<Recording> findByAdventureId(String adventureId) { return repository.findByAdventureId(adventureId).stream().map(JpaRecordingAdapter::toDomain).toList(); }
    @Override public Optional<Recording> findById(String id) { return repository.findById(id).map(JpaRecordingAdapter::toDomain); }
    @Override public List<Recording> findByStatusIn(Collection<RecordingStatus> statuses) { return repository.findByStatusIn(statuses).stream().map(JpaRecordingAdapter::toDomain).toList(); }
    private static Recording toDomain(RecordingEntity entity) { return new Recording(entity.getId(), entity.getChronicleId(), entity.getAdventureId(), entity.getSource(), entity.getStatus(), entity.getStartedAt(), entity.getEndedAt(), entity.getAudioObjectKey(), entity.getTranscriptObjectKey()); }
}
