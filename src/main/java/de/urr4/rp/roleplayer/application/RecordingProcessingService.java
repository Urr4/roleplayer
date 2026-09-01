package de.urr4.rp.roleplayer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingKeyFactory;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.port.out.AsrUnavailableException;
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
        String audioObjectKey;
        try {
            audioObjectKey = audioStore.store(recording.audioObjectKey(), audioBytes, normalizedContentType(contentType));
        } catch (Exception e) {
            log.error("Failed to store uploaded recording audio {}", recording.id(), e);
            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.FAILED, recording.startedAt(), Instant.now(), recording.audioObjectKey(),
                    recording.transcriptObjectKey(), "Failed to store the uploaded audio: " + e.getMessage()));
            throw new IllegalStateException("Failed to store uploaded recording audio " + recording.id(), e);
        }

        try {
            List<TranscriptSegment> segments = transcriptionClient.transcribe(recording.id(), audioBytes, "de", true)
                    .stream()
                    .map(transcriptSegmentRepository::save)
                    .peek(segment -> transcriptEventPublisher.publish(recording.adventureId(), segment))
                    .toList();
            String transcriptObjectKey = transcriptStore.store(recording.transcriptObjectKey(),
                    objectMapper.writeValueAsBytes(segments));

            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.DONE, recording.startedAt(), Instant.now(), audioObjectKey, transcriptObjectKey));
        } catch (AsrUnavailableException e) {
            // Audio is already safely stored in MinIO; leave the recording in
            // AWAITING_ASR so RecordingRetryScheduler picks it up once the
            // WhisperX host is reachable again - no data is lost.
            log.warn("ASR service unreachable while processing uploaded recording {}; will retry automatically",
                    recording.id(), e);
            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.AWAITING_ASR, recording.startedAt(), recording.endedAt(), audioObjectKey,
                    recording.transcriptObjectKey(),
                    "ASR service is currently unreachable; transcription will be retried automatically."));
        } catch (Exception e) {
            log.error("Failed to process uploaded recording {}", recording.id(), e);
            // Persist FAILED so polling clients can observe terminal state even when
            // the async transcription job throws.
            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.FAILED, recording.startedAt(), Instant.now(), audioObjectKey,
                    recording.transcriptObjectKey(), "Transcription failed: " + e.getMessage()));
            throw new IllegalStateException("Failed to process uploaded recording " + recording.id(), e);
        }
    }

    /**
     * Retries transcription for an upload that previously failed because the
     * ASR service was unreachable. The audio is re-fetched from MinIO since
     * the original bytes are not kept in memory.
     */
    @Async("recordingTaskExecutor")
    public void retryUpload(Recording recording, byte[] audioBytes) {
        try {
            List<TranscriptSegment> segments = transcriptionClient.transcribe(recording.id(), audioBytes, "de", true)
                    .stream()
                    .map(transcriptSegmentRepository::save)
                    .peek(segment -> transcriptEventPublisher.publish(recording.adventureId(), segment))
                    .toList();
            String transcriptObjectKey = transcriptStore.store(recording.transcriptObjectKey(),
                    objectMapper.writeValueAsBytes(segments));

            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.DONE, recording.startedAt(), Instant.now(), recording.audioObjectKey(), transcriptObjectKey));
            log.info("Successfully retried ASR transcription for recording {}", recording.id());
        } catch (AsrUnavailableException e) {
            log.debug("ASR service still unreachable while retrying recording {}", recording.id());
            // stays in AWAITING_ASR, will be retried again on the next scheduler tick
        } catch (Exception e) {
            log.error("Failed to retry transcription for recording {}", recording.id(), e);
            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.FAILED, recording.startedAt(), Instant.now(), recording.audioObjectKey(),
                    recording.transcriptObjectKey(), "Transcription failed: " + e.getMessage()));
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
    public void processDiscordFinal(Recording recording, String sessionName, List<SpeakerAudioDelta> speakerAudio,
                                    String language) {
        try {
            List<TranscriptSegment> segments = new ArrayList<>();
            for (SpeakerAudioDelta speaker : speakerAudio) {
                if (speaker.audioBytes().length == 0) {
                    continue;
                }
                String speakerLabel = normalizedSpeakerLabel(speaker.speakerLabel());
                // Each speaker's complete audio is transcribed as a single
                // pass now that the recording has stopped - transcribing on
                // a fast incremental cadence while still recording (the
                // previous approach) cut utterances mid-sentence at each
                // tick boundary and produced garbled half-sentences.
                transcriptionClient.transcribe(recording.id(), speaker.audioBytes(), language, false)
                        .stream()
                        .map(segment -> new TranscriptSegment(segment.id(), segment.recordingId(), speakerLabel,
                                segment.startMs(), segment.endMs(), segment.text(), segment.createdAt()))
                        .forEach(segments::add);
            }
            segments.sort(java.util.Comparator.comparingLong(TranscriptSegment::startMs));

            List<TranscriptSegment> saved = segments.stream()
                    .map(transcriptSegmentRepository::save)
                    .peek(segment -> transcriptEventPublisher.publish(recording.adventureId(), segment))
                    .toList();
            String transcriptObjectKey = transcriptStore.store(recording.transcriptObjectKey(),
                    objectMapper.writeValueAsBytes(saved));

            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.DONE, recording.startedAt(), recording.endedAt(), recording.audioObjectKey(), transcriptObjectKey));
        } catch (AsrUnavailableException e) {
            log.warn("ASR service unreachable while finalizing Discord recording {}; will retry automatically",
                    recording.id(), e);
            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.AWAITING_ASR, recording.startedAt(), recording.endedAt(), recording.audioObjectKey(),
                    recording.transcriptObjectKey(),
                    "ASR service is currently unreachable; transcription will be retried automatically."));
        } catch (Exception e) {
            log.error("Failed to finalize Discord recording {}", recording.id(), e);
            recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(), recording.source(),
                    RecordingStatus.FAILED, recording.startedAt(), recording.endedAt(), recording.audioObjectKey(),
                    recording.transcriptObjectKey(), "Transcription failed: " + e.getMessage()));
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
