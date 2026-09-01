package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.adapter.memory.InMemoryRecordingRepository;
import de.urr4.rp.roleplayer.adapter.memory.InMemoryChronicleRepository;
import de.urr4.rp.roleplayer.adapter.memory.InMemoryAdventureRepository;
import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.AdventureStatus;
import de.urr4.rp.roleplayer.domain.port.out.AudioStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveRecordingBufferManagerTest {

    private Path bufferDirectory;

    @AfterEach
    void cleanup() throws Exception {
        if (bufferDirectory == null || !Files.exists(bufferDirectory)) {
            return;
        }
        try (var paths = Files.walk(bufferDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test
    void stopReplacesPreviousAudioObjectAndDeletesOldKey() {
        InMemoryRecordingRepository recordingRepository = new InMemoryRecordingRepository();
        InMemoryChronicleRepository chronicleRepository = new InMemoryChronicleRepository();
        chronicleRepository.save(new Chronicle("session-1", "Campaign Session", Instant.parse("2026-08-25T09:59:00Z"), null));
        InMemoryAdventureRepository adventureRepository = new InMemoryAdventureRepository();
        adventureRepository.save(new Adventure("adventure-1", "session-1", "Adventure One", AdventureStatus.ACTIVE,
                Instant.parse("2026-08-25T09:59:00Z"), Instant.parse("2026-08-25T09:59:00Z"), null, de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus.NONE, null));

        AudioStore audioStore = mock(AudioStore.class);
        when(audioStore.store(anyString(), any(byte[].class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        RecordingProcessingService processingService = mock(RecordingProcessingService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T10:00:00Z"));
        bufferDirectory = Path.of("build", "test-live-recording-buffers", UUID.randomUUID().toString());

        LiveRecordingBufferManager manager = new LiveRecordingBufferManager(recordingRepository, chronicleRepository,
                adventureRepository, audioStore, processingService, clock, bufferDirectory);

        Recording recording = manager.start("adventure-1", RecordingSource.MICROPHONE, "webm", "audio/webm", "de",
                true, false);
        manager.appendChunk(recording.id(), new byte[]{1, 2, 3});

        clock.advance(Duration.ofSeconds(5));
        Recording paused = manager.pause(recording.id());
        String firstKey = paused.audioObjectKey();
        assertEquals("Campaign_Session/2026-08-25/100000--100005.webm", firstKey);

        manager.resume(recording.id());
        manager.appendChunk(recording.id(), new byte[]{4, 5});

        clock.advance(Duration.ofSeconds(5));
        Recording stopped = manager.stop(recording.id());

        assertEquals("Campaign_Session/2026-08-25/100000--100010.webm", stopped.audioObjectKey());
        verify(audioStore).delete(firstKey);
        ArgumentCaptor<byte[]> audioBytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(audioStore, times(2)).store(anyString(), audioBytesCaptor.capture(), anyString());
        assertEquals(2, audioBytesCaptor.getAllValues().size());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, audioBytesCaptor.getAllValues().get(1));
        verify(processingService, times(2)).processLiveWebmChunks(any(Recording.class), anyString(), any(List.class),
                anyLong(), any(Instant.class), anyString(), anyBoolean(), any(Object.class), any(Runnable.class));
        assertFalse(Files.exists(bufferDirectory.resolve(recording.id() + ".audio")));
    }

    @Test
    void pauseDoesNotAdvanceTranscriptionBoundaryBeforeAsyncCallbackRuns() {
        InMemoryRecordingRepository recordingRepository = new InMemoryRecordingRepository();
        InMemoryChronicleRepository chronicleRepository = new InMemoryChronicleRepository();
        chronicleRepository.save(new Chronicle("session-1", "Campaign Session", Instant.parse("2026-08-25T09:59:00Z"), null));
        InMemoryAdventureRepository adventureRepository = new InMemoryAdventureRepository();
        adventureRepository.save(new Adventure("adventure-1", "session-1", "Adventure One", AdventureStatus.ACTIVE,
                Instant.parse("2026-08-25T09:59:00Z"), Instant.parse("2026-08-25T09:59:00Z"), null, de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus.NONE, null));

        AudioStore audioStore = mock(AudioStore.class);
        when(audioStore.store(anyString(), any(byte[].class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        RecordingProcessingService processingService = mock(RecordingProcessingService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T10:00:00Z"));
        bufferDirectory = Path.of("build", "test-live-recording-buffers", UUID.randomUUID().toString());

        LiveRecordingBufferManager manager = new LiveRecordingBufferManager(recordingRepository, chronicleRepository,
                adventureRepository, audioStore, processingService, clock, bufferDirectory);

        Recording recording = manager.start("adventure-1", RecordingSource.MICROPHONE, "webm", "audio/webm", "de",
                true, false);
        manager.appendChunk(recording.id(), new byte[]{1, 2, 3});
        clock.advance(Duration.ofSeconds(5));
        manager.pause(recording.id());

        manager.resume(recording.id());
        manager.appendChunk(recording.id(), new byte[]{4, 5});
        clock.advance(Duration.ofSeconds(5));
        manager.pause(recording.id());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<byte[]>> chunkBytesCaptor = ArgumentCaptor.forClass(List.class);
        verify(processingService, times(2)).processLiveWebmChunks(any(Recording.class), anyString(),
                chunkBytesCaptor.capture(), anyLong(), any(Instant.class), anyString(), anyBoolean(), any(Object.class),
                any(Runnable.class));
        List<List<byte[]>> capturedChunkBatches = chunkBytesCaptor.getAllValues();
        assertEquals(1, capturedChunkBatches.get(0).size());
        assertArrayEquals(new byte[]{1, 2, 3}, capturedChunkBatches.get(0).get(0));
        // The mocked processingService never invokes the onChunksPersisted
        // callback, so the transcription boundary is never advanced - the
        // second flush should therefore still see the first (not-yet-
        // transcribed, per this mock) chunk in addition to the new one.
        assertEquals(2, capturedChunkBatches.get(1).size());
        assertArrayEquals(new byte[]{1, 2, 3}, capturedChunkBatches.get(1).get(0));
        assertArrayEquals(new byte[]{4, 5}, capturedChunkBatches.get(1).get(1));
    }

    @Test
    void pauseKeepsPreviousAudioObjectWhenRecordingSaveFails() {
        Chronicle chronicle = new Chronicle("session-1", "Campaign Session", Instant.parse("2026-08-25T09:59:00Z"), null);
        AudioStore audioStore = mock(AudioStore.class);
        when(audioStore.store(anyString(), any(byte[].class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        RecordingRepositoryState recordingState = new RecordingRepositoryState();
        var recordingRepository = mock(de.urr4.rp.roleplayer.domain.port.out.RecordingRepository.class);
        var chronicleRepository = mock(de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository.class);
        var adventureRepository = mock(de.urr4.rp.roleplayer.domain.port.out.AdventureRepository.class);
        Adventure adventure = new Adventure("adventure-1", "session-1", "Adventure One", AdventureStatus.ACTIVE,
                Instant.parse("2026-08-25T09:59:00Z"), Instant.parse("2026-08-25T09:59:00Z"), null, de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus.NONE, null);
        when(chronicleRepository.findById("session-1")).thenReturn(java.util.Optional.of(chronicle));
        when(adventureRepository.findById("adventure-1")).thenReturn(java.util.Optional.of(adventure));
        when(recordingRepository.findById(anyString())).thenAnswer(invocation ->
                java.util.Optional.ofNullable(recordingState.current.get()));
        when(recordingRepository.save(any(Recording.class))).thenAnswer(invocation -> {
            Recording saved = invocation.getArgument(0);
            recordingState.saveCount++;
            if (recordingState.saveCount == 1) {
                recordingState.current.set(saved);
                return saved;
            }
            throw new IllegalStateException("boom");
        });

        RecordingProcessingService processingService = mock(RecordingProcessingService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T10:00:00Z"));
        bufferDirectory = Path.of("build", "test-live-recording-buffers", UUID.randomUUID().toString());

        LiveRecordingBufferManager manager = new LiveRecordingBufferManager(recordingRepository, chronicleRepository,
                adventureRepository, audioStore, processingService, clock, bufferDirectory);

        Recording recording = manager.start("adventure-1", RecordingSource.MICROPHONE, "webm", "audio/webm", "de",
                true, false);
        manager.appendChunk(recording.id(), new byte[]{1, 2, 3});

        clock.advance(Duration.ofSeconds(5));
        assertThrows(IllegalStateException.class, () -> manager.pause(recording.id()));

        verify(audioStore, never()).delete(anyString());
    }

    @Test
    void stopDiscordRecordingTranscribesEachSpeakersFullBufferedAudioOnce() {
        InMemoryRecordingRepository recordingRepository = new InMemoryRecordingRepository();
        InMemoryChronicleRepository chronicleRepository = new InMemoryChronicleRepository();
        chronicleRepository.save(new Chronicle("session-1", "Campaign Session", Instant.parse("2026-08-25T09:59:00Z"), null));
        InMemoryAdventureRepository adventureRepository = new InMemoryAdventureRepository();
        adventureRepository.save(new Adventure("adventure-1", "session-1", "Adventure One", AdventureStatus.ACTIVE,
                Instant.parse("2026-08-25T09:59:00Z"), Instant.parse("2026-08-25T09:59:00Z"), null, de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus.NONE, null));

        AudioStore audioStore = mock(AudioStore.class);
        when(audioStore.store(anyString(), any(byte[].class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        RecordingProcessingService processingService = mock(RecordingProcessingService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T10:00:00Z"));
        bufferDirectory = Path.of("build", "test-live-recording-buffers", UUID.randomUUID().toString());

        LiveRecordingBufferManager manager = new LiveRecordingBufferManager(recordingRepository, chronicleRepository,
                adventureRepository, audioStore, processingService, clock, bufferDirectory);

        Recording recording = manager.start("adventure-1", RecordingSource.DISCORD, "wav", "audio/wav", "de",
                false, true, "discord-channel-1");

        // Nothing should be transcribed yet while still recording - Discord
        // audio is only ever transcribed once, at stop() time (fast
        // incremental transcription previously produced garbled
        // half-sentences by cutting utterances mid-tick).
        var sink = manager.createDiscordAudioSink(recording.id());
        sink.onUserAudio("discord-user-1", "Alice", new byte[]{1, 2, 3, 4});
        clock.advance(Duration.ofSeconds(5));
        manager.flushRecordingsDue();
        verify(processingService, never()).processDiscordFinal(any(Recording.class), anyString(), any(List.class), anyString());

        clock.advance(Duration.ofSeconds(5));
        Recording stopped = manager.stop(recording.id());
        assertEquals(de.urr4.rp.roleplayer.domain.model.RecordingStatus.PROCESSING, stopped.status());
        // Regression test: transcriptObjectKey used to stay null here (live
        // recordings, unlike uploads, never pre-generate one) and get passed
        // straight through to the object store as the storage key, which
        // crashed with "Parameter 'Key' must not be null" and left the
        // recording FAILED.
        assertNotNull(stopped.transcriptObjectKey());

        ArgumentCaptor<List<SpeakerAudioDelta>> speakerAudioCaptor = ArgumentCaptor.forClass(List.class);
        verify(processingService, times(1)).processDiscordFinal(any(Recording.class), anyString(),
                speakerAudioCaptor.capture(), anyString());
        assertEquals(1, speakerAudioCaptor.getValue().size());
        assertEquals("discord-user-1", speakerAudioCaptor.getValue().get(0).speakerId());
    }

    @Test
    void discordUserAudioGapsAreFilledWithSilenceBeforeTranscription() {
        InMemoryRecordingRepository recordingRepository = new InMemoryRecordingRepository();
        InMemoryChronicleRepository chronicleRepository = new InMemoryChronicleRepository();
        chronicleRepository.save(new Chronicle("session-1", "Campaign Session", Instant.parse("2026-08-25T09:59:00Z"), null));
        InMemoryAdventureRepository adventureRepository = new InMemoryAdventureRepository();
        adventureRepository.save(new Adventure("adventure-1", "session-1", "Adventure One", AdventureStatus.ACTIVE,
                Instant.parse("2026-08-25T09:59:00Z"), Instant.parse("2026-08-25T09:59:00Z"), null, de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus.NONE, null));

        AudioStore audioStore = mock(AudioStore.class);
        when(audioStore.store(anyString(), any(byte[].class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        RecordingProcessingService processingService = mock(RecordingProcessingService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T10:00:00Z"));
        bufferDirectory = Path.of("build", "test-live-recording-buffers", UUID.randomUUID().toString());

        LiveRecordingBufferManager manager = new LiveRecordingBufferManager(recordingRepository, chronicleRepository,
                adventureRepository, audioStore, processingService, clock, bufferDirectory);

        Recording recording = manager.start("adventure-1", RecordingSource.DISCORD, "wav", "audio/wav", "de",
                false, true, "discord-channel-1");
        var sink = manager.createDiscordAudioSink(recording.id());

        byte[] firstUtterance = new byte[]{1, 2, 3, 4};
        sink.onUserAudio("discord-user-1", "Alice", firstUtterance);

        // A real pause between two separate remarks (well above the
        // continuous-speech Opus-frame cadence) - naively concatenating the
        // next remark directly onto the first would give WhisperX no
        // acoustic cue that these are two distinct, temporally distant
        // utterances, which was blending sentence tails/starts together.
        clock.advance(Duration.ofSeconds(1));
        byte[] secondUtterance = new byte[]{5, 6, 7, 8};
        sink.onUserAudio("discord-user-1", "Alice", secondUtterance);

        clock.advance(Duration.ofSeconds(1));
        manager.stop(recording.id());

        ArgumentCaptor<List<SpeakerAudioDelta>> speakerAudioCaptor = ArgumentCaptor.forClass(List.class);
        verify(processingService, times(1)).processDiscordFinal(any(Recording.class), anyString(),
                speakerAudioCaptor.capture(), anyString());
        // The final (WAV-wrapped) audio must be noticeably longer than the
        // raw concatenation of both utterances - the extra bytes are the
        // inserted silence gap, giving WhisperX a clear pause boundary.
        int rawConcatenatedLength = firstUtterance.length + secondUtterance.length;
        assertTrue(speakerAudioCaptor.getValue().get(0).audioBytes().length > rawConcatenatedLength + 40,
                "Expected silence to be inserted for the gap between utterances");
    }

    private static final class RecordingRepositoryState {
        private final AtomicReference<Recording> current = new AtomicReference<>();
        private int saveCount;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
