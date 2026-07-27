package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataNpcRepository extends JpaRepository<NpcEntity, String> {
    List<NpcEntity> findByIdIn(List<String> ids);
}
