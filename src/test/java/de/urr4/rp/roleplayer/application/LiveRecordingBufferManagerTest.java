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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        verify(processingService, times(2)).processLiveDelta(any(Recording.class), anyString(), any(byte[].class),
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

        ArgumentCaptor<byte[]> deltaBytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(processingService, times(2)).processLiveDelta(any(Recording.class), anyString(), deltaBytesCaptor.capture(),
                anyLong(), any(Instant.class), anyString(), anyBoolean(), any(Object.class), any(Runnable.class));
        List<byte[]> capturedDeltas = deltaBytesCaptor.getAllValues();
        assertArrayEquals(new byte[]{1, 2, 3}, capturedDeltas.get(0));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, capturedDeltas.get(1));
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
    void refreshDiscordLiveTranscriptsPostsNewSpeechOnFastCadenceIndependentOfAudioFlush() {
        InMemoryRecordingRepository recordingRepository = new InMemoryRecordingRepository();
        InMemoryChronicleRepository chronicleRepository = new InMemoryChronicleRepository();
        chronicleRepository.save(new Chronicle("session-1", "Campaign Session", Instant.parse("2026-08-25T09:59:00Z"), null));
        InMemoryAdventureRepository adventureRepository = new InMemoryAdventureRepository();
        adventureRepository.save(new Adventure("adventure-1", "session-1", "Adventure One", AdventureStatus.ACTIVE,
                Instant.parse("2026-08-25T09:59:00Z"), Instant.parse("2026-08-25T09:59:00Z"), null, de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus.NONE, null));

        AudioStore audioStore = mock(AudioStore.class);
        when(audioStore.store(anyString(), any(byte[].class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        RecordingProcessingService processingService = mock(RecordingProcessingService.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(9, Runnable.class).run();
            return null;
        }).when(processingService).processDiscordLiveDelta(any(Recording.class), anyString(), any(List.class),
                anyLong(), any(Instant.class), anyString(), anyString(), anyBoolean(), any(Object.class), any(Runnable.class));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-25T10:00:00Z"));
        bufferDirectory = Path.of("build", "test-live-recording-buffers", UUID.randomUUID().toString());

        LiveRecordingBufferManager manager = new LiveRecordingBufferManager(recordingRepository, chronicleRepository,
                adventureRepository, audioStore, processingService, clock, bufferDirectory);

        Recording recording = manager.start("adventure-1", RecordingSource.DISCORD, "wav", "audio/wav", "de",
                false, true, "discord-channel-1", true);

        // No speech captured yet: the fast cadence must be a no-op.
        manager.refreshDiscordLiveTranscriptsDue();
        verify(processingService, never()).processDiscordLiveDelta(any(Recording.class), anyString(), any(List.class),
                anyLong(), any(Instant.class), anyString(), anyString(), anyBoolean(), any(Object.class), any(Runnable.class));

        var sink = manager.createDiscordAudioSink(recording.id());
        sink.onUserAudio("discord-user-1", "Alice", new byte[]{1, 2, 3, 4});

        clock.advance(Duration.ofSeconds(5));
        manager.refreshDiscordLiveTranscriptsDue();

        ArgumentCaptor<List<SpeakerAudioDelta>> deltasCaptor = ArgumentCaptor.forClass(List.class);
        verify(processingService, times(1)).processDiscordLiveDelta(any(Recording.class), anyString(), deltasCaptor.capture(),
                anyLong(), any(Instant.class), anyString(), anyString(), anyBoolean(), any(Object.class), any(Runnable.class));
        assertEquals(1, deltasCaptor.getValue().size());
        assertEquals("discord-user-1", deltasCaptor.getValue().get(0).speakerId());

        // Calling again immediately (no new speech since last call) must not
        // re-transcribe/re-post the same audio.
        manager.refreshDiscordLiveTranscriptsDue();
        verify(processingService, times(1)).processDiscordLiveDelta(any(Recording.class), anyString(), any(List.class),
                anyLong(), any(Instant.class), anyString(), anyString(), anyBoolean(), any(Object.class), any(Runnable.class));

        // The audio-store upload (S3/MinIO), by contrast, only happens when
        // the recording is stopped/paused/manually flushed - not on every
        // fast chat-transcript tick.
        verify(audioStore, never()).store(anyString(), any(byte[].class), anyString());
    }

    @Test
    void refreshDiscordLiveTranscriptsSkipsPausedRecordings() {
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
                false, true, "discord-channel-1", true);
        var sink = manager.createDiscordAudioSink(recording.id());
        sink.onUserAudio("discord-user-1", "Alice", new byte[]{1, 2, 3, 4});

        clock.advance(Duration.ofSeconds(5));
        manager.pause(recording.id());

        manager.refreshDiscordLiveTranscriptsDue();

        // The pause() call itself flushes any pending speech (processDiscordLiveDelta
        // invoked once there); the fast cadence must not run again while paused.
        verify(processingService, times(1)).processDiscordLiveDelta(any(Recording.class), anyString(), any(List.class),
                anyLong(), any(Instant.class), anyString(), anyString(), anyBoolean(), any(Object.class), any(Runnable.class));
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
