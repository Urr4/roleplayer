package de.urr4.rp.roleplayer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingKeyFactory;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.port.out.AudioStore;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptSegmentRepository;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptStore;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
class RecordingProcessingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingProcessingService.class);

    private final AudioStore audioStore;
    private final TranscriptStore transcriptStore;
    private final TranscriptionClient transcriptionClient;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final RecordingRepository recordingRepository;
    private final ObjectMapper objectMapper;
    private final TranscriptEventPublisher transcriptEventPublisher;

    RecordingProcessingService(AudioStore audioStore, TranscriptStore transcriptStore,
                               TranscriptionClient transcriptionClient,
                               TranscriptSegmentRepository transcriptSegmentRepository,
                               RecordingRepository recordingRepository, ObjectMapper objectMapper,
                               TranscriptEventPublisher transcriptEventPublisher) {
        this.audioStore = audioStore;
        this.transcriptStore = transcriptStore;
        this.transcriptionClient = transcriptionClient;
        this.transcriptSegmentRepository = transcriptSegmentRepository;
        this.recordingRepository = recordingRepository;
        this.objectMapper = objectMapper;
        this.transcriptEventPublisher = transcriptEventPublisher;
    }

    @Async("recordingTaskExecutor")
    public void processUpload(Recording recording, byte[] audioBytes, String contentType) {
        try {
            String audioObjectKey = audioStore.store(recording.audioObjectKey(), audioBytes, normalizedContentType(contentType));
            List<TranscriptSegment> segments = transcriptionClient.transcribe(recording.id(), audioBytes, "de", true)
                    .stream()
                    .map(transcriptSegmentRepository::save)
                    .peek(segment -> transcriptEventPublisher.publish(recording.adventureId(), segment))
                    .toList();
            String transcriptObjectKey = transcriptStore.store(recording.transcriptObjectKey(),
                    objectMapper.writeValueAsBytes(segments));

            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.DONE, recording.startedAt(), Instant.now(), audioObjectKey, transcriptObjectKey));
        } catch (Exception e) {
            log.error("Failed to process uploaded recording {}", recording.id(), e);
            // Persist FAILED so polling clients can observe terminal state even when
            // the async transcription job throws.
            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.FAILED, recording.startedAt(), Instant.now(), recording.audioObjectKey(),
                    recording.transcriptObjectKey()));
            throw new IllegalStateException("Failed to process uploaded recording " + recording.id(), e);
        }
    }

    @Async("recordingTaskExecutor")
    public void processLiveDelta(Recording recording, String sessionName, byte[] deltaBytes, long offsetMs,
                                 Instant flushedAt, String language, boolean diarize, Object recordingLock,
                                 Runnable onDeltaPersisted) {
        try {
            if (deltaBytes.length > 0) {
                transcriptionClient.transcribe(recording.id(), deltaBytes, language, diarize)
                        .stream()
                        .map(segment -> new TranscriptSegment(segment.id(), segment.recordingId(), segment.speakerLabel(),
                                segment.startMs() + offsetMs, segment.endMs() + offsetMs, segment.text(),
                                segment.createdAt()))
                        .map(transcriptSegmentRepository::save)
                        .forEach(segment -> transcriptEventPublisher.publish(recording.adventureId(), segment));
            }

            synchronized (recordingLock) {
                if (deltaBytes.length > 0) {
                    onDeltaPersisted.run();
                }
                refreshTranscriptObject(recording, sessionName, flushedAt);
            }
        } catch (Exception e) {
            log.warn("Failed to process live recording delta {}; transcription boundary left unchanged for retry",
                    recording.id(), e);
            throw new IllegalStateException("Failed to process live recording delta " + recording.id(), e);
        }
    }

    @Async("recordingTaskExecutor")
    public void processDiscordLiveDelta(Recording recording, String sessionName, List<SpeakerAudioDelta> speakerDeltas,
                                        long offsetMs, Instant flushedAt, String language, Object recordingLock,
                                        Runnable onDeltaPersisted) {
        try {
            List<TranscriptSegment> segmentsToPersist = new ArrayList<>();
            for (SpeakerAudioDelta speakerDelta : speakerDeltas) {
                if (speakerDelta.audioBytes().length == 0) {
                    continue;
                }
                String speakerLabel = normalizedSpeakerLabel(speakerDelta.speakerLabel());
                transcriptionClient.transcribe(recording.id(), speakerDelta.audioBytes(), language, false)
                        .stream()
                        .map(segment -> new TranscriptSegment(segment.id(), segment.recordingId(), speakerLabel,
                                segment.startMs() + offsetMs, segment.endMs() + offsetMs, segment.text(),
                                segment.createdAt()))
                        .forEach(segmentsToPersist::add);
            }

            segmentsToPersist.stream()
                    .map(transcriptSegmentRepository::save)
                    .forEach(segment -> transcriptEventPublisher.publish(recording.adventureId(), segment));

            synchronized (recordingLock) {
                if (!speakerDeltas.isEmpty()) {
                    onDeltaPersisted.run();
                }
                refreshTranscriptObject(recording, sessionName, flushedAt);
            }
        } catch (Exception e) {
            log.warn("Failed to process Discord live recording delta {}; transcription boundary left unchanged for retry",
                    recording.id(), e);
            throw new IllegalStateException("Failed to process Discord live recording delta " + recording.id(), e);
        }
    }

    private static String normalizedContentType(String contentType) {
        return contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType;
    }

    private static String normalizedSpeakerLabel(String speakerLabel) {
        return Objects.requireNonNullElse(speakerLabel, "").isBlank() ? "UNKNOWN" : speakerLabel.trim();
    }

    private void refreshTranscriptObject(Recording recording, String sessionName, Instant flushedAt) throws Exception {
        Recording latestRecording = recordingRepository.findById(recording.id())
                .orElseThrow(() -> new IllegalStateException("Recording not found during transcript update: "
                        + recording.id()));
        List<TranscriptSegment> allSegments = transcriptSegmentRepository
                .findByRecordingIdOrderByStartMsAsc(recording.id());
        String newTranscriptObjectKey = RecordingKeyFactory.create(sessionName, latestRecording.startedAt(),
                flushedAt, "json");
        String storedTranscriptObjectKey = transcriptStore.store(newTranscriptObjectKey,
                objectMapper.writeValueAsBytes(allSegments));

        recordingRepository.save(new Recording(latestRecording.id(), latestRecording.chronicleId(), latestRecording.adventureId(),
                latestRecording.source(), latestRecording.status(), latestRecording.startedAt(),
                latestRecording.endedAt(), latestRecording.audioObjectKey(), storedTranscriptObjectKey));
        if (latestRecording.transcriptObjectKey() != null
                && !latestRecording.transcriptObjectKey().equals(storedTranscriptObjectKey)) {
            try {
                transcriptStore.delete(latestRecording.transcriptObjectKey());
            } catch (RuntimeException e) {
                log.warn("Failed to delete superseded transcript object {} for recording {}",
                        latestRecording.transcriptObjectKey(), latestRecording.id(), e);
            }
        }
    }
}
