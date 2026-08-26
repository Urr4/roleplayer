package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingKeyFactory;
import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.port.out.AudioStore;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import de.urr4.rp.roleplayer.domain.port.out.VoiceChannelCapture;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveRecordingBufferManager {

    private static final Logger log = LoggerFactory.getLogger(LiveRecordingBufferManager.class);
    private static final Duration FLUSH_INTERVAL = Duration.ofMinutes(5);

    private final RecordingRepository recordingRepository;
    private final ChronicleRepository chronicleRepository;
    private final AdventureRepository adventureRepository;
    private final AudioStore audioStore;
    private final RecordingProcessingService recordingProcessingService;
    private final Clock clock;
    private final Path bufferDirectory;
    private final Map<String, ManagedRecordingBuffer> buffers = new ConcurrentHashMap<>();

    @Autowired
    public LiveRecordingBufferManager(RecordingRepository recordingRepository,
                                      ChronicleRepository chronicleRepository,
                                      AdventureRepository adventureRepository,
                                      AudioStore audioStore,
                                      RecordingProcessingService recordingProcessingService) {
        this(recordingRepository, chronicleRepository, adventureRepository, audioStore, recordingProcessingService, Clock.systemUTC(),
                Path.of("build", "live-recording-buffers"));
    }

    LiveRecordingBufferManager(RecordingRepository recordingRepository,
                               ChronicleRepository chronicleRepository,
                               AdventureRepository adventureRepository,
                               AudioStore audioStore,
                               RecordingProcessingService recordingProcessingService,
                               Clock clock,
                               Path bufferDirectory) {
        this.recordingRepository = recordingRepository;
        this.chronicleRepository = chronicleRepository;
        this.adventureRepository = adventureRepository;
        this.audioStore = audioStore;
        this.recordingProcessingService = recordingProcessingService;
        this.clock = clock;
        this.bufferDirectory = bufferDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.bufferDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create live recording buffer directory", e);
        }
    }

    public Recording start(String adventureId, RecordingSource source, String fileExtension, String contentType,
                           String language, boolean diarize, boolean storedAudioRequiresWavHeader) {
        Adventure adventure = adventureRepository.findById(adventureId)
                .orElseThrow(() -> new NoSuchElementException("Adventure not found: " + adventureId));
        chronicleRepository.findById(adventure.chronicleId())
                .orElseThrow(() -> new NoSuchElementException("Chronicle not found: " + adventure.chronicleId()));

        Instant startedAt = Instant.now(clock);
        Recording recording = new Recording(UUID.randomUUID().toString(), adventure.chronicleId(), adventureId, source,
                RecordingStatus.RECORDING, startedAt, null, null, null);
        Recording savedRecording = recordingRepository.save(recording);
        buffers.put(savedRecording.id(), new ManagedRecordingBuffer(
                createBufferFile(savedRecording.id()),
                fileExtension,
                contentType,
                language,
                diarize,
                storedAudioRequiresWavHeader));
        return savedRecording;
    }

    public VoiceChannelCapture.DiscordAudioSink createDiscordAudioSink(String recordingId) {
        requireTrackedBuffer(recordingId);
        return new VoiceChannelCapture.DiscordAudioSink() {
            @Override
            public void onCombinedAudio(byte[] pcm16BitStereo48kHz) {
                appendDiscordCombinedAudio(recordingId, pcm16BitStereo48kHz);
            }

            @Override
            public void onUserAudio(String discordUserId, String discordDisplayName, byte[] pcm16BitStereo48kHz) {
                appendDiscordUserAudio(recordingId, discordUserId, discordDisplayName, pcm16BitStereo48kHz);
            }
        };
    }

    public void appendChunk(String recordingId, byte[] chunkBytes) {
        ManagedRecordingBuffer buffer = requireTrackedBuffer(recordingId);
        synchronized (buffer) {
            Recording recording = requireRecording(recordingId);
            requireStatus(recording, RecordingStatus.RECORDING, "Recording is not currently capturing audio");
            appendBytes(buffer.audioBufferPath(), chunkBytes, recordingId);
            buffer.markCombinedChunkWritten(chunkBytes == null ? 0 : chunkBytes.length, Instant.now(clock));
        }
    }

    public Recording pause(String recordingId) {
        ManagedRecordingBuffer buffer = requireTrackedBuffer(recordingId);
        synchronized (buffer) {
            Recording recording = requireRecording(recordingId);
            requireStatus(recording, RecordingStatus.RECORDING, "Recording is not currently active");
            FlushResult flushResult = flushLocked(recording, buffer);
            buffer.pauseAt(flushResult.flushedAt());
            return saveRecording(flushResult.recording(), RecordingStatus.PAUSED, flushResult.recording().endedAt());
        }
    }

    public Recording resume(String recordingId) {
        ManagedRecordingBuffer buffer = requireTrackedBuffer(recordingId);
        synchronized (buffer) {
            Recording recording = requireRecording(recordingId);
            requireStatus(recording, RecordingStatus.PAUSED, "Recording is not paused");
            buffer.prepareForResume();
            return saveRecording(recording, RecordingStatus.RECORDING, null);
        }
    }

    public Recording stop(String recordingId) {
        ManagedRecordingBuffer buffer = requireTrackedBuffer(recordingId);
        synchronized (buffer) {
            Recording recording = requireRecording(recordingId);
            if (recording.status() != RecordingStatus.RECORDING && recording.status() != RecordingStatus.PAUSED) {
                throw new IllegalStateException("Recording cannot be stopped from status " + recording.status());
            }
            FlushResult flushResult = flushLocked(recording, buffer);
            buffer.pauseAt(flushResult.flushedAt());
            Instant endedAt = Instant.now(clock);
            Recording stoppedRecording = saveRecording(flushResult.recording(), RecordingStatus.DONE, endedAt);
            buffers.remove(recordingId);
            deleteBufferArtifacts(buffer);
            return stoppedRecording;
        }
    }

    public Recording fail(String recordingId) {
        ManagedRecordingBuffer buffer = buffers.remove(recordingId);
        Recording recording = requireRecording(recordingId);
        if (buffer != null) {
            synchronized (buffer) {
                buffer.disableCapture();
                deleteBufferArtifacts(buffer);
            }
        }
        return saveRecording(recording, RecordingStatus.FAILED, Instant.now(clock));
    }

    public void flushRecordingsDue() {
        Instant now = Instant.now(clock);
        for (Map.Entry<String, ManagedRecordingBuffer> entry : buffers.entrySet()) {
            ManagedRecordingBuffer buffer = entry.getValue();
            synchronized (buffer) {
                Recording recording;
                try {
                    recording = requireRecording(entry.getKey());
                } catch (NoSuchElementException e) {
                    buffers.remove(entry.getKey());
                    deleteBufferArtifacts(buffer);
                    continue;
                }
                if (recording.status() != RecordingStatus.RECORDING || !buffer.shouldFlushAt(now)) {
                    continue;
                }
                try {
                    flushLocked(recording, buffer);
                } catch (RuntimeException e) {
                    log.warn("Scheduled flush failed for recording {}", entry.getKey(), e);
                }
            }
        }
    }

    @PreDestroy
    void cleanupBuffers() {
        buffers.values().forEach(this::deleteBufferArtifacts);
        buffers.clear();
    }

    private void appendDiscordCombinedAudio(String recordingId, byte[] pcmBytes) {
        ManagedRecordingBuffer buffer = buffers.get(recordingId);
        if (buffer == null || pcmBytes == null || pcmBytes.length == 0) {
            return;
        }
        synchronized (buffer) {
            if (!buffer.isCaptureEnabled()) {
                return;
            }
            appendBytes(buffer.audioBufferPath(), pcmBytes, recordingId);
            buffer.markCombinedChunkWritten(pcmBytes.length, Instant.now(clock));
        }
    }

    private void appendDiscordUserAudio(String recordingId, String discordUserId, String discordDisplayName,
                                        byte[] pcmBytes) {
        ManagedRecordingBuffer buffer = buffers.get(recordingId);
        if (buffer == null || pcmBytes == null || pcmBytes.length == 0) {
            return;
        }
        synchronized (buffer) {
            if (!buffer.isCaptureEnabled()) {
                return;
            }
            Path userBufferPath = buffer.userBufferPath(discordUserId, bufferDirectory);
            appendBytes(userBufferPath, pcmBytes, recordingId);
            buffer.markUserChunkWritten(discordUserId, discordDisplayName, userBufferPath, Instant.now(clock));
        }
    }

    private FlushResult flushLocked(Recording recording, ManagedRecordingBuffer buffer) {
        ensureOpenRecording(recording);
        Instant flushedAt = Instant.now(clock);
        byte[] fullBufferBytes = readAllBytes(buffer.audioBufferPath(), recording.id());
        Chronicle chronicle = chronicleRepository.findById(recording.chronicleId())
                .orElseThrow(() -> new NoSuchElementException("Chronicle not found: " + recording.chronicleId()));
        // recording.chronicleId() is denormalized from the adventure at creation time,
        // so it stays valid for naming even though recordings are adventure-scoped.

        String newAudioObjectKey = RecordingKeyFactory.create(chronicle.name(), recording.startedAt(), flushedAt,
                buffer.fileExtension());
        byte[] storedAudioBytes = buffer.prepareStoredAudio(fullBufferBytes);
        String storedAudioObjectKey = audioStore.store(newAudioObjectKey, storedAudioBytes, buffer.contentType());
        Recording updatedRecording = recordingRepository.save(new Recording(recording.id(), recording.chronicleId(),
                recording.adventureId(), recording.source(), recording.status(), recording.startedAt(), recording.endedAt(),
                storedAudioObjectKey, recording.transcriptObjectKey()));
        if (recording.audioObjectKey() != null && !recording.audioObjectKey().equals(storedAudioObjectKey)) {
            try {
                audioStore.delete(recording.audioObjectKey());
            } catch (RuntimeException e) {
                log.warn("Failed to delete superseded audio object {} for recording {}",
                        recording.audioObjectKey(), recording.id(), e);
            }
        }

        long approximateOffsetMs = buffer.lastTranscribedApproximateMs();
        long approximateDurationMs = buffer.approximateRecordedDurationMsAt(flushedAt);

        if (buffer.storedAudioRequiresWavHeader()) {
            List<SpeakerAudioDelta> speakerDeltas = buffer.collectSpeakerDeltas(recording.id());
            recordingProcessingService.processDiscordLiveDelta(updatedRecording, chronicle.name(), speakerDeltas,
                    approximateOffsetMs, flushedAt, buffer.language(), buffer, () -> {
                        if (fullBufferBytes.length > buffer.lastTranscribedOffset()) {
                            buffer.advanceTranscriptionBoundary(fullBufferBytes.length, approximateDurationMs);
                        }
                        if (!speakerDeltas.isEmpty()) {
                            buffer.advanceSpeakerTranscriptionBoundaries();
                        }
                    });
        } else {
            long deltaStart = buffer.lastTranscribedOffset();
            if (deltaStart < 0 || deltaStart > fullBufferBytes.length) {
                throw new IllegalStateException("Invalid transcription offset for recording " + recording.id());
            }
            byte[] deltaBytes = Arrays.copyOfRange(fullBufferBytes, Math.toIntExact(deltaStart), fullBufferBytes.length);
            recordingProcessingService.processLiveDelta(updatedRecording, chronicle.name(), deltaBytes, approximateOffsetMs,
                    flushedAt, buffer.language(), buffer.diarize(), buffer, () -> {
                        if (deltaBytes.length > 0) {
                            buffer.advanceTranscriptionBoundary(fullBufferBytes.length, approximateDurationMs);
                        }
                    });
        }

        buffer.markFlushedAt(flushedAt);
        return new FlushResult(updatedRecording, flushedAt);
    }

    private Recording saveRecording(Recording recording, RecordingStatus status, Instant endedAt) {
        return recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(),
                recording.source(), status, recording.startedAt(), endedAt, recording.audioObjectKey(), recording.transcriptObjectKey()));
    }

    private Recording requireRecording(String recordingId) {
        return recordingRepository.findById(recordingId)
                .orElseThrow(() -> new NoSuchElementException("Recording not found: " + recordingId));
    }

    private ManagedRecordingBuffer requireTrackedBuffer(String recordingId) {
        ManagedRecordingBuffer buffer = buffers.get(recordingId);
        if (buffer != null) {
            return buffer;
        }
        if (recordingRepository.findById(recordingId).isPresent()) {
            throw new IllegalStateException("Recording is not in progress: " + recordingId);
        }
        throw new NoSuchElementException("Recording not found: " + recordingId);
    }

    private static void requireStatus(Recording recording, RecordingStatus expectedStatus, String message) {
        if (recording.status() != expectedStatus) {
            throw new IllegalStateException(message + ": " + recording.status());
        }
    }

    private static void ensureOpenRecording(Recording recording) {
        if (recording.status() == RecordingStatus.DONE || recording.status() == RecordingStatus.STOPPED
                || recording.status() == RecordingStatus.FAILED) {
            throw new IllegalStateException("Recording is already stopped: " + recording.id());
        }
    }

    private Path createBufferFile(String recordingId) {
        Path bufferPath = bufferDirectory.resolve(recordingId + ".audio");
        try {
            Files.deleteIfExists(bufferPath);
            return Files.createFile(bufferPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create live recording buffer for " + recordingId, e);
        }
    }

    private static byte[] readAllBytes(Path bufferPath, String recordingId) {
        try {
            return Files.exists(bufferPath) ? Files.readAllBytes(bufferPath) : new byte[0];
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read live recording buffer for " + recordingId, e);
        }
    }

    private static void appendBytes(Path bufferPath, byte[] bytes, String recordingId) {
        try {
            if (!Files.exists(bufferPath)) {
                Files.createFile(bufferPath);
            }
            Files.write(bufferPath, bytes, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append audio chunk for recording " + recordingId, e);
        }
    }

    private void deleteBufferArtifacts(ManagedRecordingBuffer buffer) {
        buffer.allBufferPaths().forEach(this::deleteBufferFile);
    }

    private void deleteBufferFile(Path bufferPath) {
        try {
            Files.deleteIfExists(bufferPath);
        } catch (IOException e) {
            log.warn("Failed to delete live recording buffer {}", bufferPath, e);
        }
    }

    private record FlushResult(Recording recording, Instant flushedAt) {
    }

    private static final class ManagedRecordingBuffer {

        private final Path audioBufferPath;
        private final String fileExtension;
        private final String contentType;
        private final String language;
        private final boolean diarize;
        private final boolean storedAudioRequiresWavHeader;
        private final Map<String, DiscordUserBuffer> discordUserBuffers = new HashMap<>();
        private Instant firstChunkAt;
        private Instant lastChunkAt;
        private Instant activeSince;
        private Instant lastFlushedAt;
        private long lastTranscribedOffset;
        private long lastTranscribedApproximateMs;
        private long accumulatedRecordedMs;
        private boolean captureEnabled = true;

        private ManagedRecordingBuffer(Path audioBufferPath, String fileExtension, String contentType, String language,
                                       boolean diarize, boolean storedAudioRequiresWavHeader) {
            this.audioBufferPath = audioBufferPath;
            this.fileExtension = fileExtension;
            this.contentType = contentType;
            this.language = language;
            this.diarize = diarize;
            this.storedAudioRequiresWavHeader = storedAudioRequiresWavHeader;
        }

        private Path audioBufferPath() {
            return audioBufferPath;
        }

        private String fileExtension() {
            return fileExtension;
        }

        private String contentType() {
            return contentType;
        }

        private String language() {
            return language;
        }

        private boolean diarize() {
            return diarize;
        }

        private boolean storedAudioRequiresWavHeader() {
            return storedAudioRequiresWavHeader;
        }

        private long lastTranscribedOffset() {
            return lastTranscribedOffset;
        }

        private long lastTranscribedApproximateMs() {
            return lastTranscribedApproximateMs;
        }

        private boolean isCaptureEnabled() {
            return captureEnabled;
        }

        private void disableCapture() {
            captureEnabled = false;
            activeSince = null;
        }

        private void markCombinedChunkWritten(int bytesWritten, Instant now) {
            markAudioActivity(now);
        }

        private void markUserChunkWritten(String discordUserId, String discordDisplayName, Path bufferPath, Instant now) {
            markAudioActivity(now);
            discordUserBuffers.compute(discordUserId, (userId, existing) -> {
                DiscordUserBuffer buffer = existing == null ? new DiscordUserBuffer(bufferPath) : existing;
                buffer.updateSpeakerLabel(discordDisplayName);
                return buffer;
            });
        }

        private void prepareForResume() {
            activeSince = null;
            captureEnabled = true;
        }

        private void pauseAt(Instant pausedAt) {
            captureEnabled = false;
            if (activeSince != null) {
                accumulatedRecordedMs += Math.max(0, Duration.between(activeSince, pausedAt).toMillis());
                activeSince = null;
            }
        }

        private long approximateRecordedDurationMsAt(Instant now) {
            long recordedMs = accumulatedRecordedMs;
            if (activeSince != null) {
                recordedMs += Math.max(0, Duration.between(activeSince, now).toMillis());
            }
            return recordedMs;
        }

        private void advanceTranscriptionBoundary(long newOffset, long approximateDurationMs) {
            lastTranscribedOffset = newOffset;
            lastTranscribedApproximateMs = approximateDurationMs;
        }

        private void advanceSpeakerTranscriptionBoundaries() {
            discordUserBuffers.values().forEach(DiscordUserBuffer::markTranscribed);
        }

        private void markFlushedAt(Instant flushedAt) {
            lastFlushedAt = flushedAt;
        }

        private boolean shouldFlushAt(Instant now) {
            if (lastChunkAt == null) {
                return false;
            }
            Instant reference = lastFlushedAt != null ? lastFlushedAt : firstChunkAt;
            return reference != null && !lastChunkAt.isBefore(reference)
                    && Duration.between(reference, now).compareTo(FLUSH_INTERVAL) >= 0;
        }

        private byte[] prepareStoredAudio(byte[] fullBufferBytes) {
            return storedAudioRequiresWavHeader ? WavFileWriter.pcm16Stereo48kHz(fullBufferBytes) : fullBufferBytes;
        }

        private Path userBufferPath(String discordUserId, Path baseDirectory) {
            DiscordUserBuffer existing = discordUserBuffers.get(discordUserId);
            if (existing != null) {
                return existing.bufferPath();
            }
            return baseDirectory.resolve(audioBufferPath.getFileName() + "--" + discordUserId + ".audio");
        }

        private List<SpeakerAudioDelta> collectSpeakerDeltas(String recordingId) {
            List<SpeakerAudioDelta> deltas = new ArrayList<>();
            for (Map.Entry<String, DiscordUserBuffer> entry : discordUserBuffers.entrySet()) {
                byte[] allUserBytes = readAllBytes(entry.getValue().bufferPath(), recordingId);
                long lastOffset = entry.getValue().lastTranscribedOffset();
                if (lastOffset < 0 || lastOffset > allUserBytes.length) {
                    throw new IllegalStateException("Invalid Discord transcription offset for recording " + recordingId);
                }
                byte[] deltaPcmBytes = Arrays.copyOfRange(allUserBytes, Math.toIntExact(lastOffset), allUserBytes.length);
                if (deltaPcmBytes.length == 0) {
                    continue;
                }
                deltas.add(new SpeakerAudioDelta(entry.getKey(), entry.getValue().speakerLabel(),
                        WavFileWriter.pcm16Stereo48kHz(deltaPcmBytes)));
            }
            return deltas;
        }

        private List<Path> allBufferPaths() {
            List<Path> paths = new ArrayList<>();
            paths.add(audioBufferPath);
            discordUserBuffers.values().stream()
                    .map(DiscordUserBuffer::bufferPath)
                    .forEach(paths::add);
            return paths;
        }

        private void markAudioActivity(Instant now) {
            if (firstChunkAt == null) {
                firstChunkAt = now;
            }
            if (activeSince == null) {
                activeSince = now;
            }
            lastChunkAt = now;
        }
    }

    private static final class DiscordUserBuffer {

        private final Path bufferPath;
        private String speakerLabel = "UNKNOWN";
        private long lastTranscribedOffset;

        private DiscordUserBuffer(Path bufferPath) {
            this.bufferPath = bufferPath;
        }

        private Path bufferPath() {
            return bufferPath;
        }

        private String speakerLabel() {
            return speakerLabel;
        }

        private long lastTranscribedOffset() {
            return lastTranscribedOffset;
        }

        private void updateSpeakerLabel(String newSpeakerLabel) {
            if (newSpeakerLabel != null && !newSpeakerLabel.isBlank()) {
                speakerLabel = newSpeakerLabel.trim();
            }
        }

        private void markTranscribed() {
            lastTranscribedOffset = bufferSize(bufferPath);
        }

        private static long bufferSize(Path bufferPath) {
            try {
                return Files.exists(bufferPath) ? Files.size(bufferPath) : 0L;
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to inspect Discord user buffer " + bufferPath, e);
            }
        }
    }
}
