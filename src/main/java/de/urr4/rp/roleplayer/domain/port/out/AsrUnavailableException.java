package de.urr4.rp.roleplayer.domain.port.out;

/**
 * Thrown by {@link TranscriptionClient} implementations when the external ASR
 * service could not be reached at all (connection refused/timeout/DNS
 * failure), as opposed to the service being reachable but rejecting the
 * request. Callers use this distinction to retry later instead of marking the
 * recording as permanently FAILED.
 */
public class AsrUnavailableException extends RuntimeException {
    public AsrUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
