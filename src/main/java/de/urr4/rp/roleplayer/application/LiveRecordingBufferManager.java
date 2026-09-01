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
import jakarta.annotation.PostConstruct;
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
        return start(adventureId, source, fileExtension, contentType, language, diarize, storedAudioRequiresWavHeader,
                null);
    }

    public Recording start(String adventureId, RecordingSource source, String fileExtension, String contentType,
                           String language, boolean diarize, boolean storedAudioRequiresWavHeader,
                           String discordChannelId) {
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
                storedAudioRequiresWavHeader,
                discordChannelId));
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
            // For WebM sources (microphone), each MediaRecorder chunk is a
            // self-contained WebM segment with its own header - raw byte
            // concatenation (above, only kept around for hasBufferedAudio()
            // bookkeeping) does not produce a valid multi-segment WebM file.
            // Keep every chunk as its own file too so (a) the final stored
            // audio can be stitched together with ffmpeg's concat demuxer,
            // which is the only reliable way to get correct duration/seek
            // metadata, and (b) each chunk can be transcribed individually -
            // see WebmRemuxer and flushLocked for details.
            if ("webm".equalsIgnoreCase(buffer.fileExtension()) && chunkBytes != null && chunkBytes.length > 0) {
                Path chunkPath = createWebmChunkFile(recordingId, buffer.nextChunkIndex());
                writeBytes(chunkPath, chunkBytes, recordingId);
                buffer.registerWebmChunkFile(chunkPath);
            }
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
            if (buffer.storedAudioRequiresWavHeader()) {
                // Discord: the whole recording is buffered per-speaker and
                // only transcribed once, right here at stop time - this
                // replaces the old fast incremental cadence, which produced
                // garbled half-sentences by transcribing audio chunks that
                // were cut mid-utterance. Doing it in one pass over each
                // speaker's complete audio lets WhisperX see full sentences.
                Chronicle chronicle = chronicleRepository.findById(recording.chronicleId())
                        .orElseThrow(() -> new NoSuchElementException("Chronicle not found: " + recording.chronicleId()));
                Recording flushedRecording = flushResult.recording();
                // Live recordings never get a transcriptObjectKey at
                // creation (unlike uploads, which pre-generate one) - it
                // stays null until the first transcript is actually stored.
                // Passing that null straight through to the object store as
                // the storage key throws ("Parameter 'Key' must not be
                // null"), which is what made every Discord recording end up
                // FAILED. Generate a real key upfront, same as uploads do.
                String transcriptObjectKey = RecordingKeyFactory.create(chronicle.name(), flushedRecording.startedAt(),
                        endedAt, "json");
                Recording processingRecording = recordingRepository.save(new Recording(flushedRecording.id(),
                        flushedRecording.chronicleId(), flushedRecording.adventureId(), flushedRecording.source(),
                        RecordingStatus.PROCESSING, flushedRecording.startedAt(), endedAt, flushedRecording.audioObjectKey(),
                        transcriptObjectKey));
                List<SpeakerAudioDelta> fullSpeakerAudio = buffer.collectFullSpeakerAudio(recordingId);
                recordingProcessingService.processDiscordFinal(processingRecording, chronicle.name(), fullSpeakerAudio,
                        buffer.language());
                buffers.remove(recordingId);
                deleteBufferArtifacts(buffer);
                return processingRecording;
            }
            Recording stoppedRecording = saveRecording(flushResult.recording(), RecordingStatus.DONE, endedAt);
            buffers.remove(recordingId);
            deleteBufferArtifacts(buffer);
            return stoppedRecording;
        }
    }

    public Recording fail(String recordingId) {
        return fail(recordingId, null);
    }

    public Recording fail(String recordingId, String errorMessage) {
        ManagedRecordingBuffer buffer = buffers.remove(recordingId);
        Recording recording = requireRecording(recordingId);
        if (buffer != null) {
            synchronized (buffer) {
                buffer.disableCapture();
                // Flush whatever audio was already captured (and kick off
                // transcription for it) before discarding the buffer -
                // otherwise a connection drop (the very reason we usually end
                // up here) silently throws away already-recorded audio with
                // no chance of ever being stored or transcribed, even once
                // the ASR service is reachable again.
                if (buffer.hasBufferedAudio() && recording.status() == RecordingStatus.RECORDING) {
                    try {
                        recording = flushLocked(recording, buffer).recording();
                    } catch (RuntimeException e) {
                        log.warn("Failed to flush buffered audio while failing recording {}", recordingId, e);
                    }
                }
                deleteBufferArtifacts(buffer);
            }
        }
        return saveRecording(recording, RecordingStatus.FAILED, Instant.now(clock), errorMessage);
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

    /**
     * The in-memory {@link #buffers} map does not survive an app restart. Any
     * recording that was left in RECORDING/PAUSED status in the repository at
     * the moment of a restart (e.g. crash, redeploy) would otherwise be stuck
     * forever: {@link #requireTrackedBuffer} always throws for it since no
     * buffer is tracked anymore, so pause/resume/stop would keep failing and
     * the frontend's Pause/Complete buttons would appear to "do nothing".
     * Reconcile such orphans to FAILED on startup so new recordings aren't
     * blocked and the UI doesn't show permanently-stuck controls.
     */
    @PostConstruct
    void reconcileOrphanedRecordingsOnStartup() {
        List<Recording> orphaned = recordingRepository
                .findByStatusIn(List.of(RecordingStatus.RECORDING, RecordingStatus.PAUSED));
        for (Recording recording : orphaned) {
            if (buffers.containsKey(recording.id())) {
                continue;
            }
            log.warn("Marking orphaned recording {} (status {}) as FAILED after restart", recording.id(),
                    recording.status());
            saveRecording(recording, RecordingStatus.FAILED, Instant.now(clock),
                    "Recording was interrupted by a server restart.");
        }
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
            Instant now = Instant.now(clock);
            Path userBufferPath = buffer.userBufferPath(discordUserId, bufferDirectory);
            // JDA only calls onUserAudio while that user's Opus audio is
            // actually flowing, so a real multi-second/minute pause between
            // two unrelated remarks would otherwise leave zero trace in this
            // per-user buffer - butting the next remark's audio directly
            // onto the end of the previous one gives WhisperX no acoustic
            // cue that these are two separate, temporally distant
            // utterances, and it was blending the tail of one sentence into
            // the start of a much later one at that boundary (a likely
            // cause of the reported inaccurate/garbled Discord
            // transcriptions). Re-insert a bounded amount of silence for any
            // real gap so WhisperX's own segmentation sees a clear pause.
            byte[] silence = buffer.silenceBytesForGap(discordUserId, now);
            if (silence.length > 0) {
                appendBytes(userBufferPath, silence, recordingId);
            }
            appendBytes(userBufferPath, pcmBytes, recordingId);
            buffer.markUserChunkWritten(discordUserId, discordDisplayName, userBufferPath, now);
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

        if (buffer.storedAudioRequiresWavHeader()) {
            // Discord: transcription only happens once, at stop() time (see
            // Recording#stop) over each speaker's complete buffered audio -
            // this periodic flush (every FLUSH_INTERVAL, plus pause/fail)
            // only needs to (re)upload the accumulated combined audio to
            // S3/MinIO so nothing is lost if the process crashes mid-session.
        } else {
            // Microphone (WebM): each MediaRecorder timeslice chunk is a
            // complete, self-contained WebM file on its own (its own
            // EBML/WebM header). Previously this branch transcribed a raw
            // byte-range slice of the flat, append-only buffer, which - once
            // that range spanned more than one chunk - is just several whole
            // WebM files concatenated back to back with no remuxing. Exactly
            // like the browser playback problem WebmRemuxer works around,
            // WhisperX's own decoder only reads the *first* embedded header
            // it finds and silently stops there, so only the very first
            // chunk (~10s) of a longer recording was ever actually
            // transcribed no matter how long the user spoke, and the
            // remainder was dropped. Transcribing each pending chunk file
            // individually sidesteps this entirely, since a lone chunk is
            // already valid on its own - see WebmRemuxer for more detail on
            // the underlying multi-segment WebM limitation.
            long approximateOffsetMs = buffer.lastTranscribedApproximateMs();
            long approximateDurationMs = buffer.approximateRecordedDurationMsAt(flushedAt);
            List<Path> pendingChunkFiles = buffer.pendingWebmChunkFiles();
            List<byte[]> pendingChunkAudio = pendingChunkFiles.stream()
                    .map(chunkPath -> readAllBytes(chunkPath, recording.id()))
                    .filter(bytes -> bytes.length > 0)
                    .toList();
            int pendingChunkCount = pendingChunkFiles.size();
            recordingProcessingService.processLiveWebmChunks(updatedRecording, chronicle.name(), pendingChunkAudio,
                    approximateOffsetMs, flushedAt, buffer.language(), buffer.diarize(), buffer, () -> {
                        if (pendingChunkCount > 0) {
                            buffer.markWebmChunksTranscribed(pendingChunkCount);
                            buffer.advanceTranscriptionBoundary(approximateDurationMs);
                        }
                    });
        }

        buffer.markFlushedAt(flushedAt);
        return new FlushResult(updatedRecording, flushedAt);
    }

    private Recording saveRecording(Recording recording, RecordingStatus status, Instant endedAt) {
        return saveRecording(recording, status, endedAt, status == RecordingStatus.FAILED ? recording.errorMessage() : null);
    }

    private Recording saveRecording(Recording recording, RecordingStatus status, Instant endedAt, String errorMessage) {
        return recordingRepository.save(new Recording(recording.id(), recording.chronicleId(), recording.adventureId(),
                recording.source(), status, recording.startedAt(), endedAt, recording.audioObjectKey(), recording.transcriptObjectKey(),
                errorMessage));
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

    private Path createWebmChunkFile(String recordingId, int chunkIndex) {
        return bufferDirectory.resolve(recordingId + "--chunk-" + chunkIndex + ".webm");
    }

    private static void writeBytes(Path path, byte[] bytes, String recordingId) {
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write WebM chunk file for recording " + recordingId, e);
        }
    }

    private static byte[] readAllBytes(Path bufferPath, String recordingId) {
        try {
            return Files.exists(bufferPath) ? Files.readAllBytes(bufferPath) : new byte[0];
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read live recording buffer for " + recordingId, e);
        }
    }

    private static byte[] readBytesFrom(Path bufferPath, long offset, String recordingId) {
        try {
            if (!Files.exists(bufferPath)) {
                return new byte[0];
            }
            long size = Files.size(bufferPath);
            if (offset < 0 || offset > size) {
                throw new IllegalStateException("Invalid Discord transcription offset for recording " + recordingId);
            }
            if (offset == size) {
                return new byte[0];
            }
            try (java.nio.channels.SeekableByteChannel channel = Files.newByteChannel(bufferPath, StandardOpenOption.READ)) {
                channel.position(offset);
                java.nio.ByteBuffer target = java.nio.ByteBuffer.allocate(Math.toIntExact(size - offset));
                while (target.hasRemaining() && channel.read(target) >= 0) {
                    // keep reading until the buffer is full or EOF
                }
                return target.array();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read live recording buffer delta for " + recordingId, e);
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
        private final String discordChannelId;
        private final Map<String, DiscordUserBuffer> discordUserBuffers = new HashMap<>();
        // Ordered list of individual WebM chunk files (microphone source
        // only) - see WebmRemuxer for why these must be kept as separate
        // files rather than only relying on the flat append-only buffer.
        private final List<Path> webmChunkFiles = new ArrayList<>();
        // How many chunks (from the front of webmChunkFiles) have already
        // been sent to WhisperX for transcription - see flushLocked/
        // pendingWebmChunkFiles for why each chunk is transcribed
        // individually rather than as one combined blob.
        private int transcribedChunkCount;
        private int nextChunkIndex;
        private Instant firstChunkAt;
        private Instant lastChunkAt;
        private Instant activeSince;
        private Instant lastFlushedAt;
        private long lastTranscribedApproximateMs;
        private long accumulatedRecordedMs;
        private boolean captureEnabled = true;

        private ManagedRecordingBuffer(Path audioBufferPath, String fileExtension, String contentType, String language,
                                       boolean diarize, boolean storedAudioRequiresWavHeader,
                                       String discordChannelId) {
            this.audioBufferPath = audioBufferPath;
            this.fileExtension = fileExtension;
            this.contentType = contentType;
            this.language = language;
            this.diarize = diarize;
            this.storedAudioRequiresWavHeader = storedAudioRequiresWavHeader;
            this.discordChannelId = discordChannelId;
        }

        private Path audioBufferPath() {
            return audioBufferPath;
        }

        private boolean hasBufferedAudio() {
            try {
                return Files.exists(audioBufferPath) && Files.size(audioBufferPath) > 0;
            } catch (IOException e) {
                return false;
            }
        }

        private int nextChunkIndex() {
            return nextChunkIndex++;
        }

        private void registerWebmChunkFile(Path chunkPath) {
            webmChunkFiles.add(chunkPath);
        }

        /**
         * Returns the WebM chunk files (in order) that have not yet been
         * transcribed. Each of these is a complete, self-contained WebM file
         * on its own and should be transcribed individually - see
         * flushLocked for why concatenating several of them into one blob
         * silently truncates transcription to just the first chunk.
         */
        private List<Path> pendingWebmChunkFiles() {
            return transcribedChunkCount >= webmChunkFiles.size()
                    ? List.of()
                    : List.copyOf(webmChunkFiles.subList(transcribedChunkCount, webmChunkFiles.size()));
        }

        private void markWebmChunksTranscribed(int count) {
            transcribedChunkCount += count;
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

        private String discordChannelId() {
            return discordChannelId;
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
                buffer.markAudioAt(now);
                return buffer;
            });
        }

        private static final int PCM_BYTES_PER_SECOND = 48_000 * 2 /* channels */ * 2 /* bytes per 16-bit sample */;
        private static final Duration MIN_GAP_TO_FILL_WITH_SILENCE = Duration.ofMillis(400);
        private static final Duration MAX_INSERTED_SILENCE = Duration.ofSeconds(2);

        private byte[] silenceBytesForGap(String discordUserId, Instant now) {
            DiscordUserBuffer userBuffer = discordUserBuffers.get(discordUserId);
            if (userBuffer == null || userBuffer.lastAudioAt() == null) {
                return new byte[0];
            }
            Duration gap = Duration.between(userBuffer.lastAudioAt(), now);
            if (gap.compareTo(MIN_GAP_TO_FILL_WITH_SILENCE) < 0) {
                return new byte[0];
            }
            Duration cappedGap = gap.compareTo(MAX_INSERTED_SILENCE) > 0 ? MAX_INSERTED_SILENCE : gap;
            long silenceByteCount = cappedGap.toMillis() * PCM_BYTES_PER_SECOND / 1000;
            silenceByteCount -= silenceByteCount % 4; // whole 16-bit stereo samples only
            return silenceByteCount > 0 ? new byte[(int) silenceByteCount] : new byte[0];
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

        private void advanceTranscriptionBoundary(long approximateDurationMs) {
            lastTranscribedApproximateMs = approximateDurationMs;
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
            if (storedAudioRequiresWavHeader) {
                return WavFileWriter.pcm16Stereo48kHz(fullBufferBytes);
            }
            // Microphone recordings arrive as a series of independent
            // MediaRecorder WebM chunks (see
            // startLiveRecording/MICROPHONE_RECORDING_EXTENSION); stitch the
            // individual chunk files (not the raw flat buffer, which is only
            // valid for byte-offset tracking) together with ffmpeg's concat
            // demuxer so duration/seeking work correctly in the browser's
            // audio player - see WebmRemuxer for why raw concatenation alone
            // does not work.
            if ("webm".equalsIgnoreCase(fileExtension)) {
                return WebmRemuxer.concatChunks(webmChunkFiles);
            }
            return fullBufferBytes;
        }

        private Path userBufferPath(String discordUserId, Path baseDirectory) {
            DiscordUserBuffer existing = discordUserBuffers.get(discordUserId);
            if (existing != null) {
                return existing.bufferPath();
            }
            return baseDirectory.resolve(audioBufferPath.getFileName() + "--" + discordUserId + ".audio");
        }

        private List<SpeakerAudioDelta> collectFullSpeakerAudio(String recordingId) {
            List<SpeakerAudioDelta> speakerAudio = new ArrayList<>();
            for (Map.Entry<String, DiscordUserBuffer> entry : discordUserBuffers.entrySet()) {
                DiscordUserBuffer userBuffer = entry.getValue();
                // Discord is only transcribed once, at stop() time (see
                // Recording#stop) - read each speaker's complete buffer from
                // the very beginning rather than an incremental tail.
                byte[] fullPcmBytes = readAllBytes(userBuffer.bufferPath(), recordingId);
                if (fullPcmBytes.length == 0) {
                    continue;
                }
                speakerAudio.add(new SpeakerAudioDelta(entry.getKey(), userBuffer.speakerLabel(),
                        WavFileWriter.pcm16Stereo48kHz(fullPcmBytes)));
            }
            return speakerAudio;
        }

        private List<Path> allBufferPaths() {
            List<Path> paths = new ArrayList<>();
            paths.add(audioBufferPath);
            paths.addAll(webmChunkFiles);
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
        private Instant lastAudioAt;

        private DiscordUserBuffer(Path bufferPath) {
            this.bufferPath = bufferPath;
        }

        private Path bufferPath() {
            return bufferPath;
        }

        private String speakerLabel() {
            return speakerLabel;
        }

        private Instant lastAudioAt() {
            return lastAudioAt;
        }

        private void markAudioAt(Instant now) {
            lastAudioAt = now;
        }

        private void updateSpeakerLabel(String newSpeakerLabel) {
            if (newSpeakerLabel != null && !newSpeakerLabel.isBlank()) {
                speakerLabel = newSpeakerLabel.trim();
            }
        }
    }
}
