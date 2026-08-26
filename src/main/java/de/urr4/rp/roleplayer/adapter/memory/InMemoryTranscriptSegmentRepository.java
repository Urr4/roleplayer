package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptSegmentRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link TranscriptSegmentRepository}, active only in the
 * {@code local} profile. Data is lost on restart.
 */
@Component
@Profile("local")
public class InMemoryTranscriptSegmentRepository implements TranscriptSegmentRepository {

    private final Map<String, TranscriptSegment> store = new ConcurrentHashMap<>();

    @Override
    public TranscriptSegment save(TranscriptSegment segment) {
        store.put(segment.id(), segment);
        return segment;
    }

    @Override
    public List<TranscriptSegment> findByRecordingIdOrderByStartMsAsc(String recordingId) {
        return store.values().stream()
                .filter(segment -> segment.recordingId().equals(recordingId))
                .sorted(Comparator.comparingLong(TranscriptSegment::startMs))
                .toList();
    }
}
