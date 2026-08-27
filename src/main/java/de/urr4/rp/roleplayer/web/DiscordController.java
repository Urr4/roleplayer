package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.RecordingService;
import de.urr4.rp.roleplayer.web.dto.DiscordGuildDto;
import de.urr4.rp.roleplayer.web.dto.DiscordVoiceChannelDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/discord")
public class DiscordController {

    private final RecordingService recordingService;

    public DiscordController(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @GetMapping("/guilds")
    public List<DiscordGuildDto> guilds() {
        return recordingService.listDiscordGuilds().stream().map(DiscordGuildDto::from).toList();
    }

    @GetMapping("/guilds/{guildId}/voice-channels")
    public ResponseEntity<List<DiscordVoiceChannelDto>> voiceChannels(@PathVariable String guildId) {
        try {
            return ResponseEntity.ok(recordingService.listDiscordVoiceChannels(guildId).stream()
                    .map(DiscordVoiceChannelDto::from).toList());
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }
}
