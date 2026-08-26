package de.urr4.rp.roleplayer.domain.port.out;

import de.urr4.rp.roleplayer.domain.model.Chronicle;

import java.util.List;
import java.util.Optional;

public interface ChronicleRepository {
    Chronicle save(Chronicle chronicle);

    List<Chronicle> findAll();

    Optional<Chronicle> findById(String id);
}
