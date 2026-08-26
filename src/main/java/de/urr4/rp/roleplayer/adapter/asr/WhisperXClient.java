package de.urr4.rp.roleplayer.adapter.asr;

import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HTTP adapter for the external WhisperX transcription service running outside
 * this application.
 */
@Component
public class WhisperXClient implements TranscriptionClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(5);

    private final RestClient restClient;
    private final String model;

    public WhisperXClient(@Value("${asr.base-url}") String baseUrl, @Value("${asr.model:large-v3}") String model) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.model = model;
    }

    @Override
    public List<TranscriptSegment> transcribe(String recordingId, byte[] audioBytes, String language, boolean diarize) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(audioBytes) {
            @Override
            public String getFilename() {
                return recordingId + ".wav";
            }
        });
        body.add("language", language);
        body.add("diarize", Boolean.toString(diarize));
        body.add("model", model);

        WhisperXResponse response = restClient.post()
                .uri("/transcribe")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> {
                    throw new IllegalStateException("WhisperX transcription failed for recording %s with HTTP %s"
                            .formatted(recordingId, clientResponse.getStatusCode().value()));
                })
                .body(WhisperXResponse.class);

        if (response == null || response.segments() == null) {
            return List.of();
        }

        Instant createdAt = Instant.now();
        return response.segments().stream()
                .map(segment -> new TranscriptSegment(
                        UUID.randomUUID().toString(),
                        recordingId,
                        segment.speaker() == null ? "UNKNOWN" : segment.speaker(),
                        toMilliseconds(segment.start()),
                        toMilliseconds(segment.end()),
                        segment.text() == null ? "" : segment.text().trim(),
                        createdAt))
                .toList();
    }

    private static long toMilliseconds(double seconds) {
        return Math.round(seconds * 1000);
    }

    private record WhisperXResponse(List<WhisperXSegmentResponse> segments) {
    }

    private record WhisperXSegmentResponse(String speaker, double start, double end, String text) {
    }
}
