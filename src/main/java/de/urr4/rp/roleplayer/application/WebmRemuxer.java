package de.urr4.rp.roleplayer.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/**
 * Repairs the WebM audio buffer accumulated by the browser's MediaRecorder
 * API before it is stored/played back.
 *
 * <p>MediaRecorder emits a live-recording as a series of independent WebM
 * "chunks" via {@code ondataavailable} (roleplayer requests one every 10s so
 * partial audio survives a crash/flush). Each chunk is itself a
 * self-contained WebM segment with its own header - naively concatenating
 * their raw bytes (as the recording buffer does, appending every chunk into
 * one file) produces a byte stream that most players/browsers can decode
 * "well enough" to hear the very first chunk's audio, but whose container
 * metadata (duration, cues/seek index) only describes that first segment.
 * That's why recordings played back through the browser showed 0:00/0:00 and
 * refused to seek even though the audio itself transcribed fine (whisperX
 * doesn't care about container metadata, only raw samples).
 *
 * <p>Fixes this by remuxing the concatenated bytes through {@code ffmpeg}
 * (stream copy, no re-encoding) into a single well-formed WebM file with
 * correct duration/seek metadata spanning the whole recording.
 */
public final class WebmRemuxer {

    private static final Logger log = LoggerFactory.getLogger(WebmRemuxer.class);
    private static final long TIMEOUT_SECONDS = 60;

    private WebmRemuxer() {
    }

    /**
     * Remuxes concatenated WebM bytes into a single valid WebM file with
     * correct duration metadata. Falls back to returning the original bytes
     * unchanged (logging a warning) if {@code ffmpeg} is not installed or
     * the remux fails for any reason, so a missing/broken ffmpeg never blocks
     * a recording from being stored - it will just keep the pre-existing
     * "duration shows 0:00" behavior instead of hard-failing.
     */
    public static byte[] fixDuration(byte[] webmBytes) {
        if (webmBytes == null || webmBytes.length == 0) {
            return webmBytes;
        }
        try {
            Process process = new ProcessBuilder(
                    "ffmpeg", "-hide_banner", "-loglevel", "error",
                    "-fflags", "+genpts",
                    "-i", "pipe:0",
                    "-c", "copy",
                    "-f", "webm",
                    "pipe:1")
                    .redirectErrorStream(false)
                    .start();

            byte[][] stdoutHolder = new byte[1][];
            Thread stdoutReader = new Thread(() -> {
                try (InputStream in = process.getInputStream()) {
                    stdoutHolder[0] = in.readAllBytes();
                } catch (IOException e) {
                    // Reported via the exit-code/timeout check below.
                }
            }, "webm-remux-stdout");
            stdoutReader.start();

            StringBuilder stderr = new StringBuilder();
            Thread stderrReader = new Thread(() -> {
                try (InputStream err = process.getErrorStream()) {
                    stderr.append(new String(err.readAllBytes()));
                } catch (IOException e) {
                    // Best-effort diagnostics only.
                }
            }, "webm-remux-stderr");
            stderrReader.start();

            try (var stdin = process.getOutputStream()) {
                stdin.write(webmBytes);
            } catch (IOException e) {
                // ffmpeg may close stdin early (e.g. it already failed to
                // parse); the exit-code check below will surface the real
                // failure reason via stderr.
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            stdoutReader.join(TimeUnit.SECONDS.toMillis(5));
            stderrReader.join(TimeUnit.SECONDS.toMillis(5));

            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg WebM remux timed out after {}s; storing the unrepaired audio buffer instead"
                        + " (duration/seeking in the player may not work correctly)", TIMEOUT_SECONDS);
                return webmBytes;
            }

            byte[] remuxed = stdoutHolder[0];
            if (process.exitValue() != 0 || remuxed == null || remuxed.length == 0) {
                log.warn("ffmpeg WebM remux failed (exit code {}): {}; storing the unrepaired audio buffer instead"
                                + " (duration/seeking in the player may not work correctly)",
                        process.exitValue(), stderr.toString().strip());
                return webmBytes;
            }
            return remuxed;
        } catch (IOException e) {
            log.warn("ffmpeg is not available to remux the recorded WebM audio ({}); storing the unrepaired"
                    + " audio buffer instead (duration/seeking in the player may not work correctly)",
                    e.getMessage());
            return webmBytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Interrupted while remuxing WebM audio", e));
        }
    }
}
