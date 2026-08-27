package de.urr4.rp.roleplayer.domain.port.out;

import java.util.List;

public interface VoiceChannelCapture {

    void joinAndCapture(String recordingId, String discordChannelId, DiscordAudioSink sink);

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
