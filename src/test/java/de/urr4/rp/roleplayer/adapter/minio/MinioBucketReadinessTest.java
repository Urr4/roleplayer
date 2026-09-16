package de.urr4.rp.roleplayer.adapter.minio;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioBucketReadinessTest {

    private static final Duration NO_DELAY = Duration.ofMillis(0);

    @Test
    void succeedsImmediatelyWhenBucketExists() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());

        MinioBucketReadiness.ensureBucketExists(s3Client, "my-bucket", 3, NO_DELAY);

        verify(s3Client, times(1)).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    void createsBucketWhenItDoesNotExistYet() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("no such bucket").build());

        MinioBucketReadiness.ensureBucketExists(s3Client, "my-bucket", 3, NO_DELAY);

        verify(s3Client, times(1)).createBucket(org.mockito.ArgumentMatchers
                .argThat((CreateBucketRequest request) -> request != null && "my-bucket".equals(request.bucket())));
    }

    @Test
    void retriesOnConnectionFailureAndEventuallySucceeds() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().message("connection refused").build())
                .thenThrow(S3Exception.builder().message("connection refused").build())
                .thenReturn(HeadBucketResponse.builder().build());

        MinioBucketReadiness.ensureBucketExists(s3Client, "my-bucket", 5, NO_DELAY);

        verify(s3Client, times(3)).headBucket(any(HeadBucketRequest.class));
    }

    @Test
    void throwsClearErrorAfterExhaustingAllAttempts() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().message("connection refused").build());

        assertThatThrownBy(() -> MinioBucketReadiness.ensureBucketExists(s3Client, "my-bucket", 3, NO_DELAY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("my-bucket")
                .hasMessageContaining("3");

        verify(s3Client, times(3)).headBucket(any(HeadBucketRequest.class));
    }
}
