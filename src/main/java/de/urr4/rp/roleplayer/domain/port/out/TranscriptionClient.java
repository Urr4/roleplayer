package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;

import java.util.List;

public interface TranscriptionClient {
    List<TranscriptSegment> transcribe(String recordingId, byte[] audioBytes, String language, boolean diarize);
}
