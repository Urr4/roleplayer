package de.urr4.rp.roleplayer.domain.port.out;

public interface TranscriptStore {
    /**
     * Stores the given transcript JSON bytes under the provided object key and
     * returns that key.
     */
    String store(String objectKey, byte[] jsonBytes);

    /**
     * Returns a URL the browser can use directly (e.g. presigned) to fetch the
     * transcript, valid for a limited time.
     */
    String presignedUrl(String objectKey);

    void delete(String objectKey);
}
