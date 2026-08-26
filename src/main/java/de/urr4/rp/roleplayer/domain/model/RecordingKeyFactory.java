package de.urr4.rp.roleplayer.domain.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public final class RecordingKeyFactory {

    private static final Pattern UNSAFE_CHARACTERS = Pattern.compile("[^A-Za-z0-9_-]+");
    private static final Pattern REPEATED_UNDERSCORES = Pattern.compile("_+");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss")
            .withZone(ZoneOffset.UTC);

    private RecordingKeyFactory() {
    }

    /**
     * Uses UTC so object keys stay stable regardless of the server's local time
     * zone.
     */
    public static String create(String sessionName, Instant startedAt, Instant endedAt, String extension) {
        return sanitizeSessionName(sessionName) + "/" + DATE_FORMATTER.format(startedAt) + "/"
                + TIME_FORMATTER.format(startedAt) + "--" + TIME_FORMATTER.format(endedAt) + "."
                + normalizeExtension(extension);
    }

    private static String sanitizeSessionName(String sessionName) {
        String sanitized = UNSAFE_CHARACTERS.matcher(sessionName == null ? "" : sessionName.trim()).replaceAll("_");
        sanitized = REPEATED_UNDERSCORES.matcher(sanitized).replaceAll("_");
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "session" : sanitized;
    }

    private static String normalizeExtension(String extension) {
        String normalized = extension == null ? "" : extension.trim();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("extension must not be blank");
        }
        return normalized;
    }
}
