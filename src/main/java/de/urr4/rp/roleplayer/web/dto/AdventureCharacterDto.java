package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.AdventureCharacter;

import java.time.Instant;

public record AdventureCharacterDto(String id, String adventureId, String characterId, Instant addedAt) {
    public static AdventureCharacterDto from(AdventureCharacter adventureCharacter) {
        return new AdventureCharacterDto(adventureCharacter.id(), adventureCharacter.adventureId(),
                adventureCharacter.characterId(), adventureCharacter.addedAt());
    }
}
