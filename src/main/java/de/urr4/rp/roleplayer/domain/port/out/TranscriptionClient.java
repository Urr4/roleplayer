package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;

import java.util.List;

public interface TranscriptionClient {
    List<TranscriptSegment> transcribe(String recordingId, byte[] audioBytes, String language, boolean diarize);

    /**
     * Lightweight reachability check used by the retry scheduler to decide
     * whether it's worth attempting transcription again for recordings stuck
     * in AWAITING_ASR.
     */
    boolean isReachable();
}
