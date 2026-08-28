package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryRecordingRepository implements RecordingRepository {
    private final Map<String, Recording> store = new ConcurrentHashMap<>();
    @Override public Recording save(Recording recording) { store.put(recording.id(), recording); return recording; }
    @Override public List<Recording> findByChronicleId(String chronicleId) { return store.values().stream().filter(recording -> recording.chronicleId().equals(chronicleId)).toList(); }
    @Override public List<Recording> findByAdventureId(String adventureId) { return store.values().stream().filter(recording -> adventureId.equals(recording.adventureId())).toList(); }
    @Override public Optional<Recording> findById(String id) { return Optional.ofNullable(store.get(id)); }
    @Override public List<Recording> findByStatusIn(Collection<RecordingStatus> statuses) { return store.values().stream().filter(recording -> statuses.contains(recording.status())).toList(); }
}
