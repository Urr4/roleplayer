package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataCharacterRepository extends JpaRepository<CharacterEntity, String> {
    List<CharacterEntity> findByIdIn(List<String> ids);
}
