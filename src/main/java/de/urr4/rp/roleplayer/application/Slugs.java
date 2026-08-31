package de.urr4.rp.roleplayer.application;

import java.util.Locale;

final class Slugs {

    private Slugs() {
    }

    static String worldSlug(String input) {
        String normalized = input == null ? "" : input.trim()
                .replace("ä", "ae")
                .replace("ö", "oe")
                .replace("ü", "ue")
                .replace("Ä", "ae")
                .replace("Ö", "oe")
                .replace("Ü", "ue")
                .replace("ß", "ss")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("World name must contain at least one letter or digit");
        }
        return normalized;
    }
}
