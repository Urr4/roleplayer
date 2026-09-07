package de.urr4.rp.roleplayer.web.dto;

/**
 * Live reachability of the external services the app depends on but does not
 * control the lifecycle of (WhisperX for transcription, Ollama for world-fact
 * extraction) - both can be unreachable at any time (e.g. the desktop PC
 * hosting them is off or on a different network) without that being a bug in
 * this app; the UI uses this to explain *why* a recording/adventure is stuck
 * and to gate the "Retry Transcription"/"Retry fact collection" buttons.
 */
public record ServiceStatusDto(boolean whisperXReachable, boolean ollamaReachable) {
}
