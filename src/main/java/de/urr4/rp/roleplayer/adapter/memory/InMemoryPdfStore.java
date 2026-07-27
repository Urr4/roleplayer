package de.urr4.rp.roleplayer.adapter.memory;

import de.urr4.rp.roleplayer.domain.port.out.PdfStore;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for {@link PdfStore}, active only in the {@code local}
 * profile so the app can run without MinIO for local dev/demo purposes. PDFs
 * are held in memory and served back via {@link DevPdfController}. Data is
 * lost on restart.
 */
@Component
@Profile("local")
public class InMemoryPdfStore implements PdfStore {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public String store(byte[] data) {
        String key = UUID.randomUUID() + ".pdf";
        store.put(key, data);
        return key;
    }

    @Override
    public String presignedUrl(String objectKey) {
        // Same-origin (proxied) URL served by DevPdfController — no real
        // presigning needed since nothing sits behind auth locally.
        return "/api/dev/pdfs/" + objectKey;
    }

    @Override
    public void delete(String objectKey) {
        store.remove(objectKey);
    }

    byte[] get(String objectKey) {
        return store.get(objectKey);
    }
}
