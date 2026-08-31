package de.urr4.rp.roleplayer.domain.model;

public record VaultNoteChange(String relativePath, String title, String markdownContent, boolean isNewFile) {
}
