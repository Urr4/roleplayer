package de.urr4.rp.roleplayer.domain.port.out;

public interface PdfStore {
    /**
     * Stores the given PDF bytes under a freshly generated object key and returns
     * that key.
     */
    String store(byte[] data);

    /**
     * Returns a URL the browser can use directly (e.g. presigned) to fetch/embed
     * the PDF, valid for a limited time.
     */
    String presignedUrl(String objectKey);

    void delete(String objectKey);
}
