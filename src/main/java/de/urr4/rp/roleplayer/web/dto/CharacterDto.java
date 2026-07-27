package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Character;

import java.time.Instant;

public record CharacterDto(String id, String name, String playerId, boolean hasSheet, Instant createdAt) {
    public static CharacterDto from(Character character) {
        return new CharacterDto(character.id(), character.name(), character.playerId(),
                character.pdfObjectKey() != null && !character.pdfObjectKey().isBlank(), character.createdAt());
    }
}
