package de.urr4.rp.roleplayer.domain.port.out;

public interface AudioStore {
    /**
     * Stores the given audio bytes under the provided object key and returns that
     * key.
     */
    String store(String objectKey, byte[] data, String contentType);

    /**
     * Retrieves the raw bytes previously stored under the given object key.
     * Used to re-attempt transcription for recordings awaiting ASR
     * availability, since the original in-memory bytes aren't retained.
     */
    byte[] fetch(String objectKey);

    /**
     * Returns a URL the browser can use directly (e.g. presigned) to fetch the
     * audio, valid for a limited time.
     */
    String presignedUrl(String objectKey);

    void delete(String objectKey);
}
