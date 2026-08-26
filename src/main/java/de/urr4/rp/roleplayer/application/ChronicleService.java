package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChronicleService {

    private final ChronicleRepository chronicleRepository;

    public ChronicleService(ChronicleRepository chronicleRepository) {
        this.chronicleRepository = chronicleRepository;
    }

    public Chronicle createChronicle(String name) {
        Chronicle chronicle = new Chronicle(UUID.randomUUID().toString(), name, Instant.now());
        return chronicleRepository.save(chronicle);
    }

    public List<Chronicle> listChronicles() {
        return chronicleRepository.findAll();
    }

    public Optional<Chronicle> getChronicle(String id) {
        return chronicleRepository.findById(id);
    }
}
