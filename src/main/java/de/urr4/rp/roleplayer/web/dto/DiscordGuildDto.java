package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.port.out.VoiceChannelCapture;

public record DiscordGuildDto(String id, String name) {
    public static DiscordGuildDto from(VoiceChannelCapture.DiscordGuild guild) {
        return new DiscordGuildDto(guild.id(), guild.name());
    }
}
