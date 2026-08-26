package de.urr4.rp.roleplayer.adapter.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "discord", name = "bot-token")
@Conditional(DiscordBotTokenCondition.class)
public class DiscordBotConfig {

    @Bean(destroyMethod = "shutdown")
    public JDA discordJda(@Value("${discord.bot-token}") String botToken) {
        return JDABuilder.createDefault(botToken)
                .enableIntents(GatewayIntent.GUILD_VOICE_STATES)
                .build();
    }
}
