package de.urr4.rp.roleplayer.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Combines the individual WebM "chunks" produced by the browser's
 * MediaRecorder API into a single, well-formed WebM file with correct
 * duration/seek metadata.
 *
 * <p><b>Only the very first chunk MediaRecorder emits via
 * {@code ondataavailable} contains the EBML/WebM header (the Segment info
 * and Tracks elements)</b>: every subsequent chunk is a headerless
 * continuation containing nothing but further Cluster elements of the same
 * still-open, unbounded Segment. This was previously assumed to be false -
 * an earlier version of this class (and matching per-chunk transcription
 * code) claimed each chunk was "a complete, self-contained WebM file" - but
 * that assumption was disproven empirically by recording real audio with a
 * real browser MediaRecorder (via Playwright + a fake audio device) and
 * inspecting the resulting chunks with ffprobe: only chunk 0 parses as a
 * valid, independent WebM file; every later chunk fails with "EBML header
 * parsing failed" when probed on its own. Chunk boundaries can even split
 * <em>mid-element</em> - a real captured sample had chunk 0 end with a
 * single trailing byte {@code 0x1F} and chunk 1 begin with {@code 43 B6 75},
 * jointly forming the true 4-byte Cluster ID {@code 1F43B675} - so there is
 * no way to reconstruct a later chunk into an independently valid file (e.g.
 * by prepending an extracted copy of chunk 0's header) either.
 *
 * <p>The only byte sequence that is ever valid WebM/Matroska is the
 * <em>complete, in-order concatenation of every chunk from chunk 0
 * onward</em>. That raw concatenation decodes and plays back its full audio
 * content correctly, but browsers/ffprobe report its duration as unknown
 * ({@code N/A}) because MediaRecorder never revisits the header to patch in
 * a final Duration element or Cues/seek index once recording stops - which
 * is exactly why the {@code <audio>} element in the browser only ever showed
 * a few seconds of duration (whatever it can infer while playing) instead of
 * the recording's real length. Running the raw concatenation through a
 * single ffmpeg remux pass ({@code ffmpeg -i concat.webm -c copy -f webm
 * out.webm}) fixes this: ffmpeg parses the whole (valid) stream once,
 * computes the real total duration, and writes a fresh, correctly-seekable
 * output file. This has been verified with real MediaRecorder output,
 * including across a pause/resume cycle.
 *
 * <p>Note this deliberately does <em>not</em> use ffmpeg's {@code concat}
 * demuxer over the individual per-chunk files (an earlier version of this
 * class did): since only chunk 0 is independently valid, the concat demuxer
 * fails to open every chunk after the first - but ffmpeg still exits {@code
 * 0} in that case, silently producing an output containing only chunk 0's
 * audio. That silent, exit-code-0 failure was the actual root cause of
 * recordings always appearing to be only as long as the first ~10s chunk.
 */
public final class WebmRemuxer {

    private static final Logger log = LoggerFactory.getLogger(WebmRemuxer.class);
    private static final long TIMEOUT_SECONDS = 60;

    private WebmRemuxer() {
    }

    /**
     * Concatenates the given WebM chunk files (in order) into a single
     * well-formed WebM file with correct duration metadata, returning its
     * bytes. Falls back to simple raw concatenation of the original chunk
     * bytes (logging a warning, i.e. the pre-existing "duration shows 0:00"
     * behavior) if {@code ffmpeg} is not installed or the remux fails for any
     * reason, so a missing/broken ffmpeg never blocks a recording from being
     * stored.
     */
    public static byte[] concatChunks(List<Path> chunkFiles) {
        List<Path> existingChunks = chunkFiles.stream().filter(Files::exists).toList();
        if (existingChunks.isEmpty()) {
            return new byte[0];
        }
        if (existingChunks.size() == 1) {
            // Nothing to stitch together; a single chunk is already a valid,
            // complete WebM file on its own (correct duration included).
            return readBytesOrEmpty(existingChunks.get(0));
        }
        return remux(concatenateRawBytes(existingChunks));
    }

    /**
     * Extracts just the audio from {@code fromMs} onward out of an already
     * valid/remuxed WebM byte stream (as returned by {@link #concatChunks}),
     * returning a new, independently valid WebM file containing only that
     * tail portion - used to transcribe only the newly-recorded audio since
     * the last successful transcription pass, instead of re-transcribing an
     * ever-growing recording from the start on every flush. Returns
     * {@code null} if {@code ffmpeg} is unavailable or the trim fails for
     * any reason (the caller should treat this as "try again on the next
     * flush" rather than losing/duplicating data).
     */
    public static byte[] extractFromOffset(byte[] validWebmBytes, long fromMs) {
        if (validWebmBytes == null || validWebmBytes.length == 0) {
            return null;
        }
        if (fromMs <= 0) {
            return validWebmBytes;
        }

        Path workDir;
        try {
            workDir = Files.createTempDirectory("webm-trim-");
        } catch (IOException e) {
            log.warn("Failed to create temp directory to trim WebM audio; skipping this flush's transcription"
                    + " delta (it will be included in the next attempt)", e);
            return null;
        }

        try {
            Path inputFile = workDir.resolve("input.webm");
            Files.write(inputFile, validWebmBytes);
            Path outputFile = workDir.resolve("output.webm");

            double fromSeconds = fromMs / 1000.0;
            Process process = new ProcessBuilder(
                    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-ss", String.format(java.util.Locale.ROOT, "%.3f", fromSeconds),
                    "-i", inputFile.toAbsolutePath().toString(),
                    "-c", "copy",
                    "-f", "webm",
                    outputFile.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg WebM trim timed out after {}s; skipping this flush's transcription delta",
                        TIMEOUT_SECONDS);
                return null;
            }
            if (process.exitValue() != 0 || !Files.exists(outputFile) || Files.size(outputFile) == 0) {
                log.warn("ffmpeg WebM trim from {}ms failed (exit code {}): {}; skipping this flush's"
                        + " transcription delta", fromMs, process.exitValue(), output.strip());
                return null;
            }
            return Files.readAllBytes(outputFile);
        } catch (IOException e) {
            log.warn("ffmpeg is not available to trim the recorded WebM audio ({}); skipping this flush's"
                    + " transcription delta", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            deleteRecursively(workDir);
        }
    }

    /**
     * Runs a single ffmpeg remux pass ({@code -c copy}, no re-encoding) over
     * an already-concatenated raw byte stream to compute and write correct
     * duration/seek metadata, returning a fresh, correctly-seekable WebM
     * file. Falls back to returning the raw bytes unchanged (duration/
     * seeking in the player may not work correctly) if ffmpeg is unavailable
     * or the remux fails for any reason - a missing/broken ffmpeg should
     * never block a recording from being stored.
     */
    private static byte[] remux(byte[] rawConcatenatedBytes) {
        Path workDir;
        try {
            workDir = Files.createTempDirectory("webm-remux-");
        } catch (IOException e) {
            log.warn("Failed to create temp directory for WebM remux; storing a raw concatenation instead"
                    + " (duration/seeking in the player may not work correctly)", e);
            return rawConcatenatedBytes;
        }

        try {
            Path inputFile = workDir.resolve("input.webm");
            Files.write(inputFile, rawConcatenatedBytes);

            // The output needs to be an actual on-disk file (not a pipe) -
            // the WebM muxer seeks back to patch the duration into the
            // header once it knows the total length, which isn't possible on
            // a non-seekable stdout pipe (verified empirically: piping to
            // stdout silently drops the duration metadata again).
            Path outputFile = workDir.resolve("output.webm");
            Process process = new ProcessBuilder(
                    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-i", inputFile.toAbsolutePath().toString(),
                    "-c", "copy",
                    "-f", "webm",
                    outputFile.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .start();

            String output;
            try (var in = process.getInputStream()) {
                output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg WebM remux timed out after {}s; storing a raw concatenation instead"
                        + " (duration/seeking in the player may not work correctly)", TIMEOUT_SECONDS);
                return rawConcatenatedBytes;
            }

            if (process.exitValue() != 0 || !Files.exists(outputFile) || Files.size(outputFile) == 0) {
                log.warn("ffmpeg WebM remux failed (exit code {}): {}; storing a raw concatenation instead"
                                + " (duration/seeking in the player may not work correctly)",
                        process.exitValue(), output.strip());
                return rawConcatenatedBytes;
            }

            return Files.readAllBytes(outputFile);
        } catch (IOException e) {
            log.warn("ffmpeg is not available to remux the recorded WebM audio ({}); storing a raw concatenation"
                    + " instead (duration/seeking in the player may not work correctly)", e.getMessage());
            return rawConcatenatedBytes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Interrupted while remuxing WebM audio", e));
        } finally {
            deleteRecursively(workDir);
        }
    }

    private static byte[] concatenateRawBytes(List<Path> chunkFiles) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (Path chunk : chunkFiles) {
                out.write(Files.readAllBytes(chunk));
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to concatenate WebM chunk files", e);
        }
    }

    private static byte[] readBytesOrEmpty(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read WebM chunk file " + path, e);
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.debug("Failed to delete temp remux file {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.debug("Failed to clean up temp remux directory {}", dir, e);
        }
    }
}
