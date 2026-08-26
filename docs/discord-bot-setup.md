# Discord bot setup

Discord voice recording uses a normal **free** Discord bot account.

## 1) Create the bot

1. Open the [Discord Developer Portal](https://discord.com/developers/applications).
2. Create a new application.
3. Add a bot user and copy its token.
4. Keep the token secret and rotate it if it is ever exposed.

## 2) Enable intents

Enable these bot settings:

- **Server Members Intent**
- **Guild Voice States** / voice-related access used for channel recording

The app itself only enables `GUILD_VOICE_STATES`, but keeping member access enabled helps Discord provide stable user/display-name context.

## 3) Invite the bot

Invite the bot to your server with permissions including:

- `Connect`
- `Speak`
- `Use Voice Activity`

Those permissions are sufficient for joining a voice channel and receiving/processing voice data.

## 4) Configure roleplayer

Set the bot token for the Spring app:

```yaml
services:
  app:
    environment:
      DISCORD_BOT_TOKEN: ${DISCORD_BOT_TOKEN:-}
```

If `DISCORD_BOT_TOKEN` is empty or unset, Discord recording stays disabled and the app still starts normally.

If `DISCORD_BOT_TOKEN` is set but invalid, the application now fails fast during startup with the underlying JDA configuration/authentication error instead of silently disabling Discord support.

## 5) ARM64 (Raspberry Pi) native Opus library caveat

`roleplayer` runs on a Raspberry Pi Docker Swarm (ARM64/aarch64). JDA's voice
support (`club.minnced:opus-java`) ships prebuilt native Opus bindings only
for common desktop/server platforms (Windows/macOS/Linux x86-64) — as of
writing there is **no official prebuilt `linux-aarch64` native binary** in the
`opus-java` Maven artifact. Without it, decoding incoming Discord voice
packets on a Pi will fail at runtime even though everything compiles fine.

Mitigation already applied in this repo's `Dockerfile`: the runtime image
installs the OS-level `libopus0` package (`apt-get install libopus0`), which
*is* available prebuilt for `arm64` via Ubuntu's package repositories. JDA's
Opus binding falls back to loading the system `libopus.so` via JNA when it
can't find a bundled native jar matching the current platform, so this
usually resolves the gap without any custom native builds.

**Verify this actually works on your Pi cluster** before relying on Discord
recording in a real session — join a test voice channel and confirm audio is
received (check the roleplayer logs for JDA/Opus warnings). If the JNA
fallback doesn't pick up the system library, the last resort is compiling
`opus-java`'s native bindings for `aarch64` yourself (clone
[discord-java/opus-java](https://github.com/discord-java/opus-java), build
against `libopus-dev`, and point the JVM at the resulting `.so` via
`-Djava.library.path`) — this is a known community workaround, not something
this repo can verify in advance.
