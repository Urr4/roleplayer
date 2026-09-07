package de.urr4.rp.roleplayer.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link WebmRemuxer} actually produces a WebM file whose
 * container-reported duration spans all chunks combined, using a single
 * continuous ffmpeg-generated WebM/Opus stream split at an arbitrary byte
 * offset to simulate real MediaRecorder chunking (rather than two
 * independently-valid WebM files, which is not how MediaRecorder actually
 * splits data: only its very first chunk has a header, every later chunk is
 * a headerless continuation, and the split point can even land mid-element -
 * see the {@link WebmRemuxer} class docs). Skips itself if ffmpeg isn't
 * installed on the machine running the tests.
 */
class WebmRemuxerTest {

    @TempDir
    Path tempDir;

    @Test
    @EnabledIf("ffmpegAvailable")
    void concatChunksProducesFileWithCombinedDuration() throws IOException, InterruptedException {
        Path fullRecording = tempDir.resolve("full.webm");
        generateSineWebm(fullRecording, 440, 4);
        byte[] fullBytes = Files.readAllBytes(fullRecording);

        // Simulate MediaRecorder's dataavailable chunking: split the single
        // continuous stream at an arbitrary interior byte offset (not
        // aligned to any element boundary) into "chunk 0" (header + start of
        // the stream) and "chunk 1" (a headerless continuation), exactly
        // like real browser output.
        int splitPoint = fullBytes.length / 2;
        Path chunk0 = tempDir.resolve("chunk0.webm");
        Path chunk1 = tempDir.resolve("chunk1.webm");
        Files.write(chunk0, java.util.Arrays.copyOfRange(fullBytes, 0, splitPoint));
        Files.write(chunk1, java.util.Arrays.copyOfRange(fullBytes, splitPoint, fullBytes.length));

        byte[] result = WebmRemuxer.concatChunks(List.of(chunk0, chunk1));

        Path outputFile = tempDir.resolve("result.webm");
        Files.write(outputFile, result);
        double duration = probeDurationSeconds(outputFile);

        // Allow small encoder/frame-boundary slack; the key assertion is
        // that the duration reflects the full ~4s recording, not just
        // whatever fraction happened to be in the first chunk, which was the
        // original bug (ffmpeg's concat demuxer silently only including
        // chunk 0 while still exiting 0).
        assertTrue(duration > 3.5, "Expected combined duration close to 4s but was " + duration);
    }

    @Test
    @EnabledIf("ffmpegAvailable")
    void extractFromOffsetReturnsOnlyTheTailPortion() throws IOException, InterruptedException {
        Path fullRecording = tempDir.resolve("full.webm");
        generateSineWebm(fullRecording, 440, 4);
        byte[] fullBytes = Files.readAllBytes(fullRecording);

        byte[] delta = WebmRemuxer.extractFromOffset(fullBytes, 2000);

        Path outputFile = tempDir.resolve("delta.webm");
        Files.write(outputFile, delta);
        double duration = probeDurationSeconds(outputFile);

        // ~2s should remain (4s total, trimmed from the 2s mark).
        assertTrue(duration > 1.5 && duration < 2.5,
                "Expected trimmed duration close to 2s but was " + duration);
    }

    static boolean ffmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static void generateSineWebm(Path output, int frequency, int durationSeconds)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "ffmpeg", "-y", "-hide_banner", "-loglevel", "error",
                "-f", "lavfi", "-i", "sine=frequency=" + frequency + ":duration=" + durationSeconds,
                "-c:a", "libopus", "-f", "webm", output.toAbsolutePath().toString())
                .start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "ffmpeg sine-wave generation timed out");
    }

    private static double probeDurationSeconds(Path file) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "ffprobe", "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", file.toAbsolutePath().toString())
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertTrue(process.waitFor(10, TimeUnit.SECONDS), "ffprobe timed out");
        return Double.parseDouble(output.strip());
    }
}
