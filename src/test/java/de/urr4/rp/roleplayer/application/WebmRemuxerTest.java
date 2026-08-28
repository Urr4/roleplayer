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
 * container-reported duration spans all chunks combined, using real
 * ffmpeg-generated WebM/Opus segments to simulate MediaRecorder chunks
 * (rather than fake byte arrays, which can't exercise the real remux path).
 * Skips itself if ffmpeg isn't installed on the machine running the tests.
 */
class WebmRemuxerTest {

    @TempDir
    Path tempDir;

    @Test
    @EnabledIf("ffmpegAvailable")
    void concatChunksProducesFileWithCombinedDuration() throws IOException, InterruptedException {
        Path seg1 = tempDir.resolve("seg1.webm");
        Path seg2 = tempDir.resolve("seg2.webm");
        generateSineWebm(seg1, 440, 2);
        generateSineWebm(seg2, 880, 2);

        byte[] result = WebmRemuxer.concatChunks(List.of(seg1, seg2));

        Path outputFile = tempDir.resolve("result.webm");
        Files.write(outputFile, result);
        double duration = probeDurationSeconds(outputFile);

        // Allow small encoder/frame-boundary slack; the key assertion is that
        // the duration reflects *both* ~2s chunks combined (~4s), not just
        // the first chunk (~2s), which was the original bug.
        assertTrue(duration > 3.5, "Expected combined duration close to 4s but was " + duration);
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
