package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.port.out.AudioStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
public class InMemoryAudioStore implements AudioStore {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public String store(String objectKey, byte[] data, String contentType) {
        store.put(objectKey, data);
        return objectKey;
    }

    @Override
    public String presignedUrl(String objectKey) {
        return objectKey;
    }

    @Override
    public byte[] fetch(String objectKey) {
        return store.getOrDefault(objectKey, new byte[0]);
    }

    @Override
    public void delete(String objectKey) {
        store.remove(objectKey);
    }
}
