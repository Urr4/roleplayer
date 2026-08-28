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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
@ConditionalOnBean(JDA.class)
public class JdaVoiceChannelCapture implements VoiceChannelCapture {

    private static final Logger log = LoggerFactory.getLogger(JdaVoiceChannelCapture.class);
    private static final long CONNECT_TIMEOUT_SECONDS = 15;

    private final JDA jda;
    private final Map<String, ActiveCapture> activeCaptures = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdogExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "discord-voice-connect-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    public JdaVoiceChannelCapture(JDA jda) {
        if (jda == null) {
            throw new IllegalStateException("Discord bot is not configured");
        }
        this.jda = jda;
    }

    @Override
    public void joinAndCapture(String recordingId, String discordChannelId, DiscordAudioSink sink,
                               Consumer<String> onConnectionLost) {
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

        // Fires at most once with a human-readable reason; guards against
        // double-invocation from both the watchdog and the status listener.
        AtomicBoolean connectionLostReported = new AtomicBoolean(false);
        AtomicBoolean everConnected = new AtomicBoolean(false);
        Runnable[] cancelWatchdogHolder = new Runnable[1];

        // Disable JDA's default auto-reconnect: if the UDP voice handshake
        // fails repeatedly (e.g. due to NAT/firewall issues on the host),
        // auto-reconnect would keep silently rejoining/leaving in a loop
        // instead of surfacing a clear failure. A ConnectionListener logs the
        // status transitions and reports a failure to the caller so it's
        // visible instead of just appearing as an endless join/leave loop
        // with no diagnostics, or a silently stuck recording.
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
                if (status == ConnectionStatus.CONNECTED) {
                    everConnected.set(true);
                    if (cancelWatchdogHolder[0] != null) {
                        cancelWatchdogHolder[0].run();
                    }
                    return;
                }
                // NOT_CONNECTED/CONNECTING_* are only transient while the
                // *initial* handshake is still in progress (the watchdog
                // covers a stall there). Once the connection has succeeded at
                // least once, a NOT_CONNECTED status means the bot actually
                // left the channel (e.g. kicked, disconnected, or the
                // connection was torn down by Discord/JDA) - previously this
                // was silently ignored, leaving the recording stuck showing
                // "ongoing" forever with an empty buffer once the user
                // eventually clicked Stop.
                boolean isTransientBeforeInitialConnect = !everConnected.get()
                        && (status == ConnectionStatus.NOT_CONNECTED || status.name().startsWith("CONNECTING_"));
                if (isTransientBeforeInitialConnect) {
                    return;
                }
                if (status.name().startsWith("CONNECTING_")) {
                    // A reconnect attempt after having been connected before; still transient.
                    return;
                }
                log.warn("Discord voice connection for recording {} in channel {} entered a disconnect/error"
                                + " state ({}); auto-reconnect is disabled, so the connection will not silently"
                                + " retry (join/leave loop).",
                        recordingId, discordChannelId, status);
                reportConnectionLost(recordingId, onConnectionLost, connectionLostReported,
                        describeConnectionStatus(status));
            }
        });

        audioManager.setReceivingHandler(handler);
        audioManager.openAudioConnection(channel);

        // JDA's openAudioConnection() is fire-and-forget: it never throws for
        // permission errors, unreachable voice regions, or the bot being
        // rate-limited, and previously left recordings stuck silently
        // "recording" forever with no diagnostics if the handshake stalled
        // (this is the "joins then instantly leaves with no error" bug). A
        // watchdog ensures such failures are surfaced within a bounded time.
        ScheduledFuture<?> watchdog = watchdogExecutor.schedule(() -> {
            if (!everConnected.get()) {
                log.warn("Discord voice connection for recording {} in channel {} did not become CONNECTED within"
                                + " {}s; treating as a failed join (check bot permissions - Connect/Speak - and"
                                + " that the channel isn't full).",
                        recordingId, discordChannelId, CONNECT_TIMEOUT_SECONDS);
                reportConnectionLost(recordingId, onConnectionLost, connectionLostReported,
                        "Discord bot could not join the voice channel within " + CONNECT_TIMEOUT_SECONDS
                                + "s. Check that it has Connect/Speak permissions and the channel isn't full.");
            }
        }, CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        cancelWatchdogHolder[0] = () -> watchdog.cancel(false);
    }

    private void reportConnectionLost(String recordingId, Consumer<String> onConnectionLost,
                                      AtomicBoolean alreadyReported, String reason) {
        if (!alreadyReported.compareAndSet(false, true)) {
            return;
        }
        try {
            onConnectionLost.accept(reason);
        } catch (RuntimeException e) {
            log.error("Failed to report Discord connection loss for recording {}", recordingId, e);
        }
    }

    private static String describeConnectionStatus(ConnectionStatus status) {
        return switch (status) {
            case NOT_CONNECTED -> "The Discord bot left the voice channel unexpectedly.";
            case DISCONNECTED_LOST_PERMISSION -> "The Discord bot lost permission to join/speak in the voice channel.";
            case DISCONNECTED_CHANNEL_DELETED -> "The Discord voice channel was deleted.";
            case DISCONNECTED_REMOVED_FROM_GUILD -> "The Discord bot was removed from the server.";
            case DISCONNECTED_KICKED_FROM_CHANNEL -> "The Discord bot was kicked from the voice channel.";
            case DISCONNECTED_AUTHENTICATION_FAILURE -> "Discord voice authentication failed.";
            case ERROR_LOST_CONNECTION -> "The Discord voice connection was lost unexpectedly.";
            case ERROR_CANNOT_RESUME -> "The Discord voice connection could not be resumed.";
            case ERROR_WEBSOCKET_UNABLE_TO_CONNECT -> "Could not establish the Discord voice websocket connection.";
            case ERROR_UNSUPPORTED_ENCRYPTION_MODES -> "Discord voice server offered no supported encryption mode.";
            case ERROR_UDP_UNABLE_TO_CONNECT -> "Could not establish the Discord voice UDP connection"
                    + " (check firewall/NAT settings on the host running the bot).";
            case ERROR_CONNECTION_TIMEOUT -> "The Discord voice connection timed out.";
            default -> "Discord voice connection failed (" + status + ").";
        };
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

