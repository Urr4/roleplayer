package de.urr4.rp.roleplayer.adapter.jpa;

import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptSegmentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("!local")
public class JpaTranscriptSegmentAdapter implements TranscriptSegmentRepository {

    private final SpringDataTranscriptSegmentRepository repository;

    public JpaTranscriptSegmentAdapter(SpringDataTranscriptSegmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public TranscriptSegment save(TranscriptSegment segment) {
        TranscriptSegmentEntity saved = repository.save(new TranscriptSegmentEntity(segment.id(), segment.recordingId(),
                segment.speakerLabel(), segment.startMs(), segment.endMs(), segment.text(), segment.createdAt()));
        return toDomain(saved);
    }

    @Override
    public List<TranscriptSegment> findByRecordingIdOrderByStartMsAsc(String recordingId) {
        return repository.findByRecordingIdOrderByStartMsAsc(recordingId).stream()
                .map(JpaTranscriptSegmentAdapter::toDomain)
                .toList();
    }

    @Override
    public void deleteByRecordingId(String recordingId) {
        repository.deleteByRecordingId(recordingId);
    }

    private static TranscriptSegment toDomain(TranscriptSegmentEntity entity) {
        return new TranscriptSegment(entity.getId(), entity.getRecordingId(), entity.getSpeakerLabel(),
                entity.getStartMs(), entity.getEndMs(), entity.getText(), entity.getCreatedAt());
    }
}
