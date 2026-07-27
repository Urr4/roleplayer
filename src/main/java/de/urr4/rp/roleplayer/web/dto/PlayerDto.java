package de.urr4.rp.roleplayer.web.dto;

import de.urr4.rp.roleplayer.domain.model.Player;

public record PlayerDto(String id, String name) {
    public static PlayerDto from(Player player) {
        return new PlayerDto(player.id(), player.name());
    }
}
