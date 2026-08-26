package de.urr4.rp.roleplayer.domain.port.out;

public interface VoiceChannelCapture {

    void joinAndCapture(String recordingId, String discordChannelId, DiscordAudioSink sink);

    void leave(String recordingId);

    interface DiscordAudioSink {
        void onCombinedAudio(byte[] pcm16BitStereo48kHz);

        void onUserAudio(String discordUserId, String discordDisplayName, byte[] pcm16BitStereo48kHz);
    }
}
