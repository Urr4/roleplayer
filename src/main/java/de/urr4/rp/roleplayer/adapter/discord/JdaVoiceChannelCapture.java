package de.urr4.rp.roleplayer.adapter.discord;

import de.urr4.rp.roleplayer.domain.port.out.VoiceChannelCapture;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.audio.UserAudio;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.audio.hooks.ConnectionListener;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnBean(JDA.class)
public class JdaVoiceChannelCapture implements VoiceChannelCapture {

    private static final Logger log = LoggerFactory.getLogger(JdaVoiceChannelCapture.class);

    private final JDA jda;
    private final Map<String, ActiveCapture> activeCaptures = new ConcurrentHashMap<>();

    public JdaVoiceChannelCapture(JDA jda) {
        if (jda == null) {
            throw new IllegalStateException("Discord bot is not configured");
        }
        this.jda = jda;
    }

    @Override
    public void joinAndCapture(String recordingId, String discordChannelId, DiscordAudioSink sink) {
        if (jda == null) {
            throw new IllegalStateException("Discord bot is not configured");
        }
        VoiceChannel channel = jda.getChannelById(VoiceChannel.class, discordChannelId);
        if (channel == null) {
            throw new NoSuchElementException("Discord voice channel not found: " + discordChannelId);
        }

        Guild guild = channel.getGuild();
        AudioManager audioManager = guild.getAudioManager();
        AudioReceiveHandler handler = new AudioReceiveHandler() {
            @Override
            public boolean canReceiveCombined() {
                return true;
            }

            @Override
            public void handleCombinedAudio(CombinedAudio combinedAudio) {
                sink.onCombinedAudio(combinedAudio.getAudioData(1.0));
            }

            @Override
            public boolean canReceiveUser() {
                return true;
            }

            @Override
            public void handleUserAudio(UserAudio userAudio) {
                sink.onUserAudio(userAudio.getUser().getId(), userAudio.getUser().getEffectiveName(),
                        userAudio.getAudioData(1.0));
            }
        };

        ActiveCapture previousCapture = activeCaptures.put(recordingId, new ActiveCapture(guild, audioManager, handler));
        if (previousCapture != null) {
            previousCapture.close();
        }

        // Disable JDA's default auto-reconnect: if the UDP voice handshake
        // fails repeatedly (e.g. due to NAT/firewall issues on the host),
        // auto-reconnect would keep silently rejoining/leaving in a loop
        // instead of surfacing a clear failure. A ConnectionListener logs the
        // status transitions so failures are visible instead of just
        // appearing as an endless join/leave loop with no diagnostics.
        audioManager.setAutoReconnect(false);
        audioManager.setConnectionListener(new ConnectionListener() {
            @Override
            public void onPing(long ping) {
                // no-op
            }

            @Override
            public void onStatusChange(ConnectionStatus status) {
                log.info("Discord voice connection status for recording {} in channel {}: {}", recordingId,
                        discordChannelId, status);
                boolean isTransientOrConnected = status == ConnectionStatus.CONNECTED
                        || status == ConnectionStatus.NOT_CONNECTED
                        || status.name().startsWith("CONNECTING_");
                if (!isTransientOrConnected) {
                    log.warn("Discord voice connection for recording {} in channel {} entered a disconnect/error"
                                    + " state ({}); auto-reconnect is disabled, so the connection will not silently"
                                    + " retry (join/leave loop). Investigate Discord bot permissions/network before"
                                    + " restarting the recording.",
                            recordingId, discordChannelId, status);
                }
            }
        });

        audioManager.setReceivingHandler(handler);
        audioManager.openAudioConnection(channel);
    }

    @Override
    public void leave(String recordingId) {
        ActiveCapture capture = activeCaptures.remove(recordingId);
        if (capture != null) {
            capture.close();
        }
    }

    @Override
    public List<DiscordGuild> listGuilds() {
        return jda.getGuilds().stream()
                .map(guild -> new DiscordGuild(guild.getId(), guild.getName()))
                .toList();
    }

    @Override
    public List<DiscordVoiceChannel> listVoiceChannelsWithParticipants(String guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            throw new NoSuchElementException("Discord guild not found: " + guildId);
        }
        return guild.getVoiceChannels().stream()
                .map(channel -> new DiscordVoiceChannel(channel.getId(), channel.getName(), channel.getMembers().size()))
                .filter(channel -> channel.participantCount() >= 1)
                .sorted(Comparator.comparing(DiscordVoiceChannel::name))
                .toList();
    }

    @Override
    public void sendChatMessage(String channelId, String text) {
        VoiceChannel channel = jda.getChannelById(VoiceChannel.class, channelId);
        if (channel == null) {
            throw new NoSuchElementException("Discord voice channel not found: " + channelId);
        }
        channel.sendMessage(text).queue();
    }

    private record ActiveCapture(Guild guild, AudioManager audioManager, AudioReceiveHandler handler) {
        private void close() {
            audioManager.setReceivingHandler(null);
            audioManager.closeAudioConnection();
        }
    }
}
