package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataTranscriptSegmentRepository extends JpaRepository<TranscriptSegmentEntity, String> {
    List<TranscriptSegmentEntity> findByRecordingIdOrderByStartMsAsc(String recordingId);
}
