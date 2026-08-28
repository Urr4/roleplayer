package de.urr4.rp.roleplayer.domain.port.out;

import java.util.List;

public interface VoiceChannelCapture {

    /**
     * Joins the given voice channel and starts capturing audio into
     * {@code sink}. {@code onConnectionLost} is invoked at most once, from a
     * background thread, if the voice connection fails to establish or drops
     * unexpectedly (e.g. missing permissions, network/NAT issues) so the
     * caller can mark the recording as failed with a meaningful reason
     * instead of leaving it stuck silently.
     */
    void joinAndCapture(String recordingId, String discordChannelId, DiscordAudioSink sink,
                        java.util.function.Consumer<String> onConnectionLost);

    void leave(String recordingId);

    List<DiscordGuild> listGuilds();

    List<DiscordVoiceChannel> listVoiceChannelsWithParticipants(String guildId);

    void sendChatMessage(String channelId, String text);

    interface DiscordAudioSink {
        void onCombinedAudio(byte[] pcm16BitStereo48kHz);

        void onUserAudio(String discordUserId, String discordDisplayName, byte[] pcm16BitStereo48kHz);
    }

    record DiscordGuild(String id, String name) {
    }

    record DiscordVoiceChannel(String id, String name, int participantCount) {
    }
}
