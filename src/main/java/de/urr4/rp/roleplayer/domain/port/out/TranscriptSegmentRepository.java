package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;

import java.util.List;

public interface TranscriptSegmentRepository {
    TranscriptSegment save(TranscriptSegment segment);

    List<TranscriptSegment> findByRecordingIdOrderByStartMsAsc(String recordingId);

    void deleteByRecordingId(String recordingId);
}
