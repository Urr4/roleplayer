package de.urr4.rp.roleplayer.adapter.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.audio.AudioNatives;
import net.dv8tion.jda.api.requests.GatewayIntent;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "discord", name = "bot-token")
@Conditional(DiscordBotTokenCondition.class)
public class DiscordBotConfig {

    private static final Logger log = LoggerFactory.getLogger(DiscordBotConfig.class);

    @Bean(destroyMethod = "shutdown")
    public JDA discordJda(@Value("${discord.bot-token}") String botToken) {
        // Since March 2026, Discord requires all voice connections to speak
        // the DAVE (Discord Audio & Video End-to-End Encryption) protocol.
        // Without it, the voice websocket handshake is rejected immediately
        // with close code 4017 right after CONNECTING_AWAITING_AUTHENTICATION
        // - which looks exactly like "the bot joins a channel and instantly
        // leaves again" from the outside. See docs/discord-bot-setup.md #5.
        boolean daveAvailable = true;
        try {
            NativeDaveFactory.ensureAvailable();
        } catch (RuntimeException e) {
            daveAvailable = false;
            log.error("Discord voice support: the native DAVE (E2EE) library could NOT be loaded on this host."
                    + " Discord voice recording will fail with close code 4017 (the bot will join a voice"
                    + " channel and be disconnected again almost immediately) because Discord now requires"
                    + " DAVE for all voice connections. Ensure the matching moe.kyokobot.libdave natives-* "
                    + "artifact for this platform/architecture is on the classpath - see"
                    + " docs/discord-bot-setup.md #5.", e);
        }

        JDABuilder builder = JDABuilder.createDefault(botToken)
                .enableIntents(GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MESSAGES);
        if (daveAvailable) {
            builder.setAudioModuleConfig(new AudioModuleConfig()
                    .withDaveSessionFactory(new LDJDADaveSessionFactory(new NativeDaveFactory())));
        }
        JDA jda = builder.build();

        // Discord voice recording silently fails (bot joins a voice channel
        // and is disconnected again almost immediately, with no obvious
        // error) if the native Opus codec isn't usable on this host - log
        // this loudly and early at startup instead of only discovering it
        // the next time someone tries to record, see
        // docs/discord-bot-setup.md #5 for the ARM64/Raspberry Pi caveat.
        if (AudioNatives.ensureOpus()) {
            log.info("Discord voice support: native Opus codec loaded successfully.");
        } else {
            log.error("Discord voice support: the native Opus codec library could NOT be loaded on this host."
                    + " Discord voice recording will fail (the bot will join a voice channel and be disconnected"
                    + " again almost immediately). Ensure libopus0 (or an equivalent native Opus library) is"
                    + " installed - see docs/discord-bot-setup.md #5.");
        }
        if (daveAvailable) {
            log.info("Discord voice support: native DAVE (E2EE) library loaded successfully - voice connections"
                    + " will use the mandatory Discord E2EE protocol.");
        }
        return jda;
    }
}

