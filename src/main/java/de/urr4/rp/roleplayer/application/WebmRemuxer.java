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
import java.util.stream.Collectors;

/**
 * Combines the individual WebM "chunks" produced by the browser's
 * MediaRecorder API into a single, well-formed WebM file with correct
 * duration/seek metadata.
 *
 * <p>MediaRecorder emits a live recording as a series of independent WebM
 * segments via {@code ondataavailable} (roleplayer requests one every 10s so
 * partial audio survives a crash/flush) - each chunk has its own complete
 * EBML/WebM header. <b>Naively concatenating their raw bytes into one file
 * does not produce a valid multi-segment WebM</b>: ffmpeg's (and every
 * browser's) matroska/webm demuxer only reads the *first* embedded header it
 * finds and stops at that first segment's end, ignoring everything appended
 * afterward - even piping the whole concatenated blob back through
 * {@code ffmpeg -i pipe:0 -c copy} does not fix this, since the demuxer has
 * already given up after the first segment by the time it remuxes. This was
 * verified empirically (an earlier version of this class attempted exactly
 * that pipe-based remux and was confirmed via manual ffprobe testing to
 * still report only the first chunk's duration) and explains why recordings
 * played back through the browser showed 0:00/0:00 and refused to seek, even
 * though transcription still worked (WhisperX only needs to decode whichever
 * chunk's samples happen to be in a given delta - it doesn't care about
 * overall container duration/seek metadata).
 *
 * <p>The only reliable fix is ffmpeg's {@code concat} demuxer, which is
 * explicitly designed to stitch together a sequence of separate same-codec
 * files into one output container with correct combined duration. This
 * requires the original chunks to still exist as separate files on disk (raw
 * byte concatenation cannot be un-done afterward), so
 * {@link LiveRecordingBufferManager} keeps each incoming chunk as its own
 * file in addition to the flat append-only buffer used for transcription
 * delta offsets.
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

        Path workDir;
        try {
            workDir = Files.createTempDirectory("webm-remux-");
        } catch (IOException e) {
            log.warn("Failed to create temp directory for WebM remux; storing a raw concatenation instead"
                    + " (duration/seeking in the player may not work correctly)", e);
            return concatenateRawBytes(existingChunks);
        }

        try {
            Path listFile = workDir.resolve("chunks.txt");
            String listContent = existingChunks.stream()
                    .map(chunk -> "file '" + chunk.toAbsolutePath() + "'")
                    .collect(Collectors.joining("\n"));
            Files.writeString(listFile, listContent, StandardCharsets.UTF_8);

            // The concat demuxer needs an actual on-disk output file (not a
            // pipe) - the WebM muxer seeks back to patch the duration into
            // the header once it knows the total length, which isn't
            // possible on a non-seekable stdout pipe (verified empirically:
            // piping to stdout silently drops the duration metadata again).
            Path outputFile = workDir.resolve("output.webm");
            Process process = new ProcessBuilder(
                    "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "concat", "-safe", "0",
                    "-i", listFile.toAbsolutePath().toString(),
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
                log.warn("ffmpeg WebM concat timed out after {}s; storing a raw concatenation instead"
                        + " (duration/seeking in the player may not work correctly)", TIMEOUT_SECONDS);
                return concatenateRawBytes(existingChunks);
            }

            if (process.exitValue() != 0 || !Files.exists(outputFile) || Files.size(outputFile) == 0) {
                log.warn("ffmpeg WebM concat failed (exit code {}): {}; storing a raw concatenation instead"
                                + " (duration/seeking in the player may not work correctly)",
                        process.exitValue(), output.strip());
                return concatenateRawBytes(existingChunks);
            }

            return Files.readAllBytes(outputFile);
        } catch (IOException e) {
            log.warn("ffmpeg is not available to remux the recorded WebM audio ({}); storing a raw concatenation"
                    + " instead (duration/seeking in the player may not work correctly)", e.getMessage());
            return concatenateRawBytes(existingChunks);
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
