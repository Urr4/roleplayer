package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.port.out.VoiceChannelCapture;

public record DiscordVoiceChannelDto(String id, String name, int participantCount) {
    public static DiscordVoiceChannelDto from(VoiceChannelCapture.DiscordVoiceChannel channel) {
        return new DiscordVoiceChannelDto(channel.id(), channel.name(), channel.participantCount());
    }
}
