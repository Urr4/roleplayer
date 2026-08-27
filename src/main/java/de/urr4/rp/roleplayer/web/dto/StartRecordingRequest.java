package de.urr4.rp.roleplayer.web.dto;

public record StartRecordingRequest(String source, String discordChannelId, Boolean writeTranscriptToChat) {

    public boolean resolvedWriteTranscriptToChat() {
        return Boolean.TRUE.equals(writeTranscriptToChat);
    }
}
