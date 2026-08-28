package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import de.urr4.rp.roleplayer.domain.port.out.AudioStore;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically retries transcription for uploaded recordings that are stuck
 * in AWAITING_ASR because the local WhisperX host was unreachable at upload
 * time (e.g. the desktop PC was off or on a different network). The audio is
 * already safely stored in MinIO, so nothing is lost while waiting.
 */
@Component
public class RecordingRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordingRetryScheduler.class);

    private final RecordingRepository recordingRepository;
    private final TranscriptionClient transcriptionClient;
    private final AudioStore audioStore;
    private final RecordingProcessingService recordingProcessingService;

    public RecordingRetryScheduler(RecordingRepository recordingRepository, TranscriptionClient transcriptionClient,
                                    AudioStore audioStore, RecordingProcessingService recordingProcessingService) {
        this.recordingRepository = recordingRepository;
        this.transcriptionClient = transcriptionClient;
        this.audioStore = audioStore;
        this.recordingProcessingService = recordingProcessingService;
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    public void retryAwaitingAsr() {
        List<Recording> awaitingAsr = recordingRepository.findByStatusIn(List.of(RecordingStatus.AWAITING_ASR));
        if (awaitingAsr.isEmpty()) {
            return;
        }
        if (!transcriptionClient.isReachable()) {
            log.debug("ASR service still unreachable; {} recording(s) remain in AWAITING_ASR", awaitingAsr.size());
            return;
        }

        log.info("ASR service reachable again; retrying transcription for {} recording(s)", awaitingAsr.size());
        for (Recording recording : awaitingAsr) {
            if (recording.audioObjectKey() == null) {
                continue;
            }
            byte[] audioBytes = audioStore.fetch(recording.audioObjectKey());
            recordingProcessingService.retryUpload(recording, audioBytes);
        }
    }
}
