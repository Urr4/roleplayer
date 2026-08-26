package de.urr4.rp.roleplayer.application;

public record SpeakerAudioDelta(String speakerId, String speakerLabel, byte[] audioBytes) {
}
