package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingKeyFactory;
import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.port.out.AudioStore;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptSegmentRepository;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptStore;
import de.urr4.rp.roleplayer.domain.port.out.VoiceChannelCapture;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class RecordingService {

    private static final String MICROPHONE_RECORDING_EXTENSION = "webm";
    private static final String MICROPHONE_RECORDING_CONTENT_TYPE = "audio/webm";
    private static final String DISCORD_RECORDING_EXTENSION = "wav";
    private static final String DISCORD_RECORDING_CONTENT_TYPE = "audio/wav";
    private static final String TRANSCRIPTION_LANGUAGE = "de";
    private static final Set<RecordingStatus> LIVE_STATUSES = Set.of(RecordingStatus.RECORDING, RecordingStatus.PAUSED);

    private final RecordingRepository recordingRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final ChronicleRepository chronicleRepository;
    private final AdventureRepository adventureRepository;
    private final RecordingProcessingService recordingProcessingService;
    private final LiveRecordingBufferManager liveRecordingBufferManager;
    private final Optional<VoiceChannelCapture> voiceChannelCapture;
    private final AudioStore audioStore;
    private final TranscriptStore transcriptStore;

    public RecordingService(RecordingRepository recordingRepository,
                            TranscriptSegmentRepository transcriptSegmentRepository,
                            ChronicleRepository chronicleRepository,
                            AdventureRepository adventureRepository,
                            RecordingProcessingService recordingProcessingService,
                            LiveRecordingBufferManager liveRecordingBufferManager,
                            Optional<VoiceChannelCapture> voiceChannelCapture,
                            AudioStore audioStore,
                            TranscriptStore transcriptStore) {
        this.recordingRepository = recordingRepository;
        this.transcriptSegmentRepository = transcriptSegmentRepository;
        this.chronicleRepository = chronicleRepository;
        this.adventureRepository = adventureRepository;
        this.recordingProcessingService = recordingProcessingService;
        this.liveRecordingBufferManager = liveRecordingBufferManager;
        this.voiceChannelCapture = voiceChannelCapture;
        this.audioStore = audioStore;
        this.transcriptStore = transcriptStore;
    }

    public Recording uploadAndTranscribe(String adventureId, String originalFilename, byte[] audioBytes,
                                         String contentType) {
        Adventure adventure = adventureRepository.findById(adventureId)
                .orElseThrow(() -> new NoSuchElementException("Adventure not found: " + adventureId));
        Chronicle chronicle = chronicleRepository.findById(adventure.chronicleId())
                .orElseThrow(() -> new NoSuchElementException("Chronicle not found: " + adventure.chronicleId()));

        Instant startedAt = Instant.now();
        Recording recording = new Recording(
                UUID.randomUUID().toString(),
                adventure.chronicleId(),
                adventureId,
                RecordingSource.UPLOAD,
                RecordingStatus.PROCESSING,
                startedAt,
                null,
                RecordingKeyFactory.create(chronicle.name(), startedAt, startedAt, fileExtensionOf(originalFilename)),
                RecordingKeyFactory.create(chronicle.name(), startedAt, startedAt, "json"));
        Recording savedRecording = recordingRepository.save(recording);
        recordingProcessingService.processUpload(savedRecording, audioBytes, contentType);
        return savedRecording;
    }

    public Recording startLiveRecording(String adventureId, RecordingSource source, String discordChannelId,
                                        boolean writeTranscriptToChat) {
        if (source == RecordingSource.MICROPHONE) {
            return liveRecordingBufferManager.start(adventureId, source, MICROPHONE_RECORDING_EXTENSION,
                    MICROPHONE_RECORDING_CONTENT_TYPE, TRANSCRIPTION_LANGUAGE, true, false);
        }
        if (source != RecordingSource.DISCORD) {
            throw new IllegalArgumentException("Live recording source not yet supported: " + source);
        }
        if (discordChannelId == null || discordChannelId.isBlank()) {
            throw new IllegalArgumentException("discordChannelId is required for Discord recordings");
        }

        VoiceChannelCapture capture = voiceChannelCapture
                .orElseThrow(() -> new IllegalStateException("Discord bot is not configured"));
        String trimmedChannelId = discordChannelId.trim();
        Recording recording = liveRecordingBufferManager.start(adventureId, source, DISCORD_RECORDING_EXTENSION,
                DISCORD_RECORDING_CONTENT_TYPE, TRANSCRIPTION_LANGUAGE, false, true, trimmedChannelId,
                writeTranscriptToChat);
        try {
            capture.joinAndCapture(recording.id(), trimmedChannelId,
                    liveRecordingBufferManager.createDiscordAudioSink(recording.id()),
                    reason -> handleDiscordConnectionLost(recording.id(), trimmedChannelId, reason));
            try {
                capture.sendChatMessage(trimmedChannelId, "Ich beginne mit der Aufzeichnung!");
            } catch (RuntimeException e) {
                // Best-effort: failing to announce shouldn't abort an otherwise-working recording.
            }
            return recording;
        } catch (RuntimeException e) {
            liveRecordingBufferManager.fail(recording.id(), "Failed to join Discord voice channel: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Invoked (from a background thread) when the Discord voice connection
     * for a live recording fails to establish or drops unexpectedly. Marks
     * the recording as FAILED with a human-readable reason so it stops
     * silently "recording" with dead Pause/Complete buttons, and leaves the
     * Discord channel so the bot doesn't linger connected with no capture.
     */
    private void handleDiscordConnectionLost(String recordingId, String discordChannelId, String reason) {
        voiceChannelCapture.ifPresent(capture -> capture.leave(recordingId));
        liveRecordingBufferManager.fail(recordingId, reason);
    }

    public List<VoiceChannelCapture.DiscordGuild> listDiscordGuilds() {
        return voiceChannelCapture.map(VoiceChannelCapture::listGuilds).orElseGet(List::of);
    }

    public List<VoiceChannelCapture.DiscordVoiceChannel> listDiscordVoiceChannels(String guildId) {
        return voiceChannelCapture
                .orElseThrow(() -> new IllegalStateException("Discord bot is not configured"))
                .listVoiceChannelsWithParticipants(guildId);
    }

    public void appendLiveChunk(String recordingId, byte[] chunkBytes) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new NoSuchElementException("Recording not found: " + recordingId));
        if (recording.source() != RecordingSource.MICROPHONE) {
            throw new IllegalStateException("Browser audio chunks are only supported for microphone recordings");
        }
        liveRecordingBufferManager.appendChunk(recordingId, chunkBytes);
    }

    public Recording pauseLiveRecording(String recordingId) {
        return liveRecordingBufferManager.pause(recordingId);
    }

    public Recording resumeLiveRecording(String recordingId) {
        return liveRecordingBufferManager.resume(recordingId);
    }

    public Recording stopLiveRecording(String recordingId) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new NoSuchElementException("Recording not found: " + recordingId));
        if (recording.source() != RecordingSource.DISCORD) {
            return liveRecordingBufferManager.stop(recordingId);
        }
        try {
            return liveRecordingBufferManager.stop(recordingId);
        } finally {
            voiceChannelCapture.ifPresent(capture -> capture.leave(recordingId));
        }
    }

    public List<Recording> listRecordings(String adventureId) {
        return recordingRepository.findByAdventureId(adventureId);
    }

    public Recording getRecording(String recordingId) {
        return recordingRepository.findById(recordingId)
                .orElseThrow(() -> new NoSuchElementException("Recording not found: " + recordingId));
    }

    public List<TranscriptSegment> getTranscript(String recordingId) {
        return transcriptSegmentRepository.findByRecordingIdOrderByStartMsAsc(recordingId);
    }

    /**
     * Aggregates the transcript across every recording made for an adventure, in
     * recording-start order. This is a simplification: segments within each
     * recording are already chronological, and recordings themselves rarely
     * overlap in practice, so sorting recordings by {@code startedAt} and
     * concatenating their segments gives a faithful reading order without
     * needing to recompute absolute wall-clock timestamps per segment.
     */
    public List<TranscriptSegment> getAdventureTranscript(String adventureId) {
        return recordingRepository.findByAdventureId(adventureId).stream()
                .sorted(Comparator.comparing(Recording::startedAt))
                .flatMap(recording -> transcriptSegmentRepository
                        .findByRecordingIdOrderByStartMsAsc(recording.id()).stream())
                .toList();
    }

    public void deleteRecording(String recordingId) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new NoSuchElementException("Recording not found: " + recordingId));
        if (LIVE_STATUSES.contains(recording.status())) {
            throw new IllegalStateException("Recording is still active; stop it before deleting");
        }
        deleteRecordingArtifacts(recording);
        transcriptSegmentRepository.deleteByRecordingId(recordingId);
        recordingRepository.deleteById(recordingId);
    }

    public Recording deleteTranscript(String recordingId) {
        Recording recording = recordingRepository.findById(recordingId)
                .orElseThrow(() -> new NoSuchElementException("Recording not found: " + recordingId));
        if (LIVE_STATUSES.contains(recording.status())) {
            throw new IllegalStateException("Recording is still active; stop it before deleting its transcript");
        }
        transcriptSegmentRepository.deleteByRecordingId(recordingId);
        if (recording.transcriptObjectKey() != null && !recording.transcriptObjectKey().isBlank()) {
            transcriptStore.delete(recording.transcriptObjectKey());
        }
        Recording updated = new Recording(recording.id(), recording.chronicleId(), recording.adventureId(),
                recording.source(), recording.status(), recording.startedAt(), recording.endedAt(),
                recording.audioObjectKey(), null, recording.errorMessage());
        return recordingRepository.save(updated);
    }

    /**
     * Cascade-deletes every recording (and its audio/transcript artifacts) that
     * belongs to the given adventure. Used when an Adventure or its owning
     * Chronicle is deleted.
     */
    void deleteRecordingsByAdventureId(String adventureId) {
        for (Recording recording : recordingRepository.findByAdventureId(adventureId)) {
            if (LIVE_STATUSES.contains(recording.status())) {
                throw new IllegalStateException(
                        "Recording " + recording.id() + " is still active; stop it before deleting");
            }
        }
        for (Recording recording : recordingRepository.findByAdventureId(adventureId)) {
            deleteRecordingArtifacts(recording);
            transcriptSegmentRepository.deleteByRecordingId(recording.id());
            recordingRepository.deleteById(recording.id());
        }
    }

    private void deleteRecordingArtifacts(Recording recording) {
        if (recording.audioObjectKey() != null && !recording.audioObjectKey().isBlank()) {
            audioStore.delete(recording.audioObjectKey());
        }
        if (recording.transcriptObjectKey() != null && !recording.transcriptObjectKey().isBlank()) {
            transcriptStore.delete(recording.transcriptObjectKey());
        }
    }

    private static String fileExtensionOf(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "audio";
        }
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == originalFilename.length() - 1) {
            return "audio";
        }
        String extension = originalFilename.substring(lastDot + 1).trim();
        return extension.isBlank() ? "audio" : extension;
    }
}
