package de.urr4.rp.roleplayer.adapter.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataWorldRepository extends JpaRepository<WorldEntity, String> {
}
