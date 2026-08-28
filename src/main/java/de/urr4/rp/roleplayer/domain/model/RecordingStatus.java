package de.urr4.rp.roleplayer.domain.model;

public enum RecordingStatus {
    RECORDING,
    PAUSED,
    STOPPED,
    PROCESSING,
    /**
     * Transcription could not reach the external ASR (WhisperX) service —
     * the audio is safely stored and will be retried automatically once the
     * service becomes reachable again (see RecordingRetryScheduler).
     */
    AWAITING_ASR,
    DONE,
    FAILED
}
