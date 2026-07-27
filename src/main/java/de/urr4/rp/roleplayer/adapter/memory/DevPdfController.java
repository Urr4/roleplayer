package de.urr4.rp.roleplayer.adapter.memory;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves PDFs held by {@link InMemoryPdfStore} back to the browser. Only
 * wired up in the {@code local} profile — production uses MinIO presigned
 * URLs directly instead.
 */
@RestController
@RequestMapping("/api/dev/pdfs")
@Profile("local")
public class DevPdfController {

    private final InMemoryPdfStore pdfStore;

    public DevPdfController(InMemoryPdfStore pdfStore) {
        this.pdfStore = pdfStore;
    }

    @GetMapping("/{key}")
    public ResponseEntity<byte[]> get(@PathVariable String key) {
        byte[] data = pdfStore.get(key);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + key + "\"")
                .body(data);
    }
}
