package de.urr4.rp.roleplayer.adapter.minio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.time.Duration;

/**
 * Shared "ensure bucket exists" logic for the MinIO adapters, with a retry
 * backoff. `docker stack deploy` only orders container starts via
 * {@code depends_on} - it does not wait for MinIO to actually be reachable
 * (Swarm mode has no readiness-gate equivalent) - so MinIO's listener may
 * still be starting up the moment these adapter beans are constructed.
 * Without retrying, the very first connection attempt failing (e.g.
 * "Connection refused") used to crash the whole Spring context and pause
 * the Swarm rollout on any transient MinIO startup delay.
 */
final class MinioBucketReadiness {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketReadiness.class);

    static final int DEFAULT_MAX_ATTEMPTS = 12;
    static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);

    private MinioBucketReadiness() {
    }

    static void ensureBucketExists(S3Client s3Client, String bucket) {
        ensureBucketExists(s3Client, bucket, DEFAULT_MAX_ATTEMPTS, DEFAULT_RETRY_DELAY);
    }

    static void ensureBucketExists(S3Client s3Client, String bucket, int maxAttempts, Duration retryDelay) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                headOrCreateBucket(s3Client, bucket);
                return;
            } catch (SdkException e) {
                if (attempt == maxAttempts) {
                    throw new IllegalStateException("MinIO bucket '" + bucket + "' was still not reachable after "
                            + maxAttempts + " attempts (" + maxAttempts * retryDelay.toSeconds()
                            + "s total) - is the minio service up and reachable?", e);
                }
                log.warn("MinIO not reachable yet (attempt {}/{}) - retrying in {}s: {}", attempt, maxAttempts,
                        retryDelay.toSeconds(), e.getMessage());
                sleepQuietly(retryDelay);
            }
        }
    }

    private static void headOrCreateBucket(S3Client s3Client, String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for MinIO to become reachable", e);
        }
    }
}
