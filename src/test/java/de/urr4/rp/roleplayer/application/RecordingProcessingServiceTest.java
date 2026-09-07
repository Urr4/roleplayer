package de.urr4.rp.roleplayer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.port.out.AudioStore;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptSegmentRepository;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptStore;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptionClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecordingProcessingServiceTest {

    @Test
    void processLiveWebmAudioLeavesBoundaryUnchangedWhenTranscriptionFailsMidRecording() {
        AudioStore audioStore = mock(AudioStore.class);
        TranscriptStore transcriptStore = mock(TranscriptStore.class);
        TranscriptionClient transcriptionClient = mock(TranscriptionClient.class);
        TranscriptSegmentRepository transcriptSegmentRepository = mock(TranscriptSegmentRepository.class);
        RecordingRepository recordingRepository = mock(RecordingRepository.class);
        TranscriptEventPublisher transcriptEventPublisher = mock(TranscriptEventPublisher.class);
        when(transcriptionClient.transcribe(any(), any(byte[].class), any(), any(Boolean.class)))
                .thenThrow(new IllegalStateException("whisperx down"));

        RecordingProcessingService service = new RecordingProcessingService(audioStore, transcriptStore,
                transcriptionClient, transcriptSegmentRepository, recordingRepository, new ObjectMapper(),
                transcriptEventPublisher);
        Recording recording = new Recording("recording-1", "session-1", "adventure-1", RecordingSource.MICROPHONE,
                RecordingStatus.RECORDING, Instant.parse("2026-08-25T10:00:00Z"), null, "audio-old", null);

        AtomicInteger boundaryAdvanceCount = new AtomicInteger();

        // Not the final flush (isFinal=false) - a transient failure mid
        // recording must not throw or change the recording's status, since
        // the next scheduled flush will simply retry with a wider delta.
        service.processLiveWebmAudio(recording, "Campaign Session", new byte[]{1, 2, 3}, 0,
                Instant.parse("2026-08-25T10:05:00Z"), "de", true, false, new Object(),
                boundaryAdvanceCount::incrementAndGet);

        assertEquals(0, boundaryAdvanceCount.get());
        verifyNoInteractions(transcriptStore, transcriptSegmentRepository, recordingRepository, transcriptEventPublisher);
    }

    @Test
    void processLiveWebmAudioMarksRecordingAwaitingAsrWhenAsrUnavailableOnFinalFlush() {
        AudioStore audioStore = mock(AudioStore.class);
        TranscriptStore transcriptStore = mock(TranscriptStore.class);
        TranscriptionClient transcriptionClient = mock(TranscriptionClient.class);
        TranscriptSegmentRepository transcriptSegmentRepository = mock(TranscriptSegmentRepository.class);
        RecordingRepository recordingRepository = mock(RecordingRepository.class);
        TranscriptEventPublisher transcriptEventPublisher = mock(TranscriptEventPublisher.class);
        when(transcriptionClient.transcribe(any(), any(byte[].class), any(), any(Boolean.class)))
                .thenThrow(new de.urr4.rp.roleplayer.domain.port.out.AsrUnavailableException("whisperx unreachable",
                        new java.io.IOException("connection refused")));
        when(recordingRepository.save(any(Recording.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecordingProcessingService service = new RecordingProcessingService(audioStore, transcriptStore,
                transcriptionClient, transcriptSegmentRepository, recordingRepository, new ObjectMapper(),
                transcriptEventPublisher);
        Recording recording = new Recording("recording-1", "session-1", "adventure-1", RecordingSource.MICROPHONE,
                RecordingStatus.RECORDING, Instant.parse("2026-08-25T10:00:00Z"), Instant.parse("2026-08-25T10:05:00Z"),
                "audio-old", null);

        // Final flush (isFinal=true, from stop()) - the app must not fail
        // outright when WhisperX is unreachable at the end of a recording;
        // instead the recording should be left in AWAITING_ASR so it can be
        // retried automatically (or via the manual "Retry Transcription"
        // button) once WhisperX is reachable again.
        service.processLiveWebmAudio(recording, "Campaign Session", new byte[]{1, 2, 3}, 0,
                Instant.parse("2026-08-25T10:05:00Z"), "de", true, true, new Object(), () -> { });

        ArgumentCaptor<Recording> savedRecording = ArgumentCaptor.forClass(Recording.class);
        verify(recordingRepository).save(savedRecording.capture());
        assertEquals(RecordingStatus.AWAITING_ASR, savedRecording.getValue().status());
    }

    @Test
    void processLiveWebmAudioSerializesTranscriptRefreshPerRecording() throws Exception {
        AudioStore audioStore = mock(AudioStore.class);
        TranscriptionClient transcriptionClient = mock(TranscriptionClient.class);
        when(transcriptionClient.transcribe(any(), any(byte[].class), any(), any(Boolean.class))).thenReturn(List.of());
        TranscriptEventPublisher transcriptEventPublisher = mock(TranscriptEventPublisher.class);
        TranscriptSegmentRepository transcriptSegmentRepository = mock(TranscriptSegmentRepository.class);
        when(transcriptSegmentRepository.findByRecordingIdOrderByStartMsAsc("recording-1")).thenReturn(List.of());

        AtomicReference<Recording> currentRecording = new AtomicReference<>(new Recording("recording-1", "session-1",
                "adventure-1", RecordingSource.MICROPHONE, RecordingStatus.RECORDING, Instant.parse("2026-08-25T10:00:00Z"), null,
                "audio-old", "transcript-old"));
        RecordingRepository recordingRepository = new RecordingRepository() {
            @Override
            public Recording save(Recording recording) {
                currentRecording.set(recording);
                return recording;
            }

            @Override
            public List<Recording> findByChronicleId(String chronicleId) {
                return List.of();
            }

            @Override
            public List<Recording> findByAdventureId(String adventureId) {
                return List.of();
            }

            @Override
            public Optional<Recording> findById(String id) {
                return Optional.ofNullable(currentRecording.get());
            }

            @Override
            public List<Recording> findByStatusIn(java.util.Collection<de.urr4.rp.roleplayer.domain.model.RecordingStatus> statuses) {
                return List.of();
            }

            @Override
            public void deleteById(String id) {
                currentRecording.set(null);
            }
        };

        AtomicInteger concurrentStores = new AtomicInteger();
        AtomicInteger maxConcurrentStores = new AtomicInteger();
        TranscriptStore transcriptStore = new TranscriptStore() {
            @Override
            public String store(String objectKey, byte[] jsonBytes) {
                int active = concurrentStores.incrementAndGet();
                maxConcurrentStores.accumulateAndGet(active, Math::max);
                try {
                    Thread.sleep(75);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                } finally {
                    concurrentStores.decrementAndGet();
                }
                return objectKey;
            }

            @Override
            public String presignedUrl(String objectKey) {
                return objectKey;
            }

            @Override
            public void delete(String objectKey) {
            }
        };

        RecordingProcessingService service = new RecordingProcessingService(audioStore, transcriptStore,
                transcriptionClient, transcriptSegmentRepository, recordingRepository, new ObjectMapper(),
                transcriptEventPublisher);
        Recording recording = currentRecording.get();
        Object recordingLock = new Object();
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> awaitAndProcess(service, recording, recordingLock, startGate));
            Future<?> second = executor.submit(() -> awaitAndProcess(service, recording, recordingLock, startGate));

            startGate.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, maxConcurrentStores.get());
        assertTrue(currentRecording.get().transcriptObjectKey() != null);
    }

    private static void awaitAndProcess(RecordingProcessingService service, Recording recording, Object recordingLock,
                                        CountDownLatch startGate) {
        try {
            assertTrue(startGate.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        service.processLiveWebmAudio(recording, "Campaign Session", new byte[]{1, 2, 3}, 0,
                Instant.parse("2026-08-25T10:05:00Z"), "de", true, false, recordingLock, () -> {
                });
    }
}
