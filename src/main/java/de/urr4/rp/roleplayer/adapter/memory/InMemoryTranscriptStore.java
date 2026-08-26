package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.port.out.TranscriptStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryTranscriptStore implements TranscriptStore {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public String store(String objectKey, byte[] jsonBytes) {
        store.put(objectKey, jsonBytes);
        return objectKey;
    }

    @Override
    public String presignedUrl(String objectKey) {
        return objectKey;
    }

    @Override
    public void delete(String objectKey) {
        store.remove(objectKey);
    }
}
