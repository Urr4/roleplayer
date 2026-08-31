package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.RecordingService;
import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.port.out.AudioStore;
import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.web.dto.RecordingDto;
import de.urr4.rp.roleplayer.web.dto.StartRecordingRequest;
import de.urr4.rp.roleplayer.web.dto.TranscriptSegmentDto;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/adventures/{adventureId}/recordings")
public class RecordingController {

    private final RecordingService recordingService;
    private final AudioStore audioStore;

    public RecordingController(RecordingService recordingService, AudioStore audioStore) {
        this.recordingService = recordingService;
        this.audioStore = audioStore;
    }

    private RecordingDto toDto(Recording recording) {
        // Proxy audio playback through this app's own (HTTPS) endpoint rather
        // than handing the browser a raw MinIO presigned URL: the app runs
        // behind HTTPS on 3502, but MinIO is only reachable over plain HTTP
        // on 9004, so a direct MinIO URL is "mixed content" and gets silently
        // blocked by the browser for <audio>/<video> elements - this is what
        // caused every recording to show as 0:00/0:00 and refuse to play.
        String audioUrl = recording.audioObjectKey() == null || recording.audioObjectKey().isBlank()
                ? null
                : ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/api/adventures/{adventureId}/recordings/{recordingId}/audio")
                        .buildAndExpand(recording.adventureId(), recording.id())
                        .toUriString();
        return RecordingDto.from(recording, audioUrl);
    }

    @GetMapping
    public List<RecordingDto> list(@PathVariable String adventureId) {
        return recordingService.listRecordings(adventureId).stream().map(this::toDto).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecordingDto> upload(@PathVariable String adventureId, @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.accepted()
                    .body(toDto(recordingService.uploadAndTranscribe(adventureId, file.getOriginalFilename(),
                            file.getBytes(), file.getContentType())));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PostMapping("/start")
    public ResponseEntity<RecordingDto> start(@PathVariable String adventureId,
                                              @RequestBody(required = false) StartRecordingRequest request) {
        try {
            RecordingSource source = parseStartSource(request);
            String discordChannelId = parseDiscordChannelId(source, request);
            boolean writeTranscriptToChat = request != null && request.resolvedWriteTranscriptToChat();
            return ResponseEntity.accepted()
                    .body(toDto(recordingService.startLiveRecording(adventureId, source, discordChannelId,
                            writeTranscriptToChat)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(path = "/{recordingId}/chunk", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> chunk(@PathVariable String recordingId, @RequestBody byte[] chunk) {
        try {
            recordingService.appendLiveChunk(recordingId, chunk);
            return ResponseEntity.accepted().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/{recordingId}/pause")
    public ResponseEntity<RecordingDto> pause(@PathVariable String recordingId) {
        try {
            return ResponseEntity.ok(toDto(recordingService.pauseLiveRecording(recordingId)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/{recordingId}/resume")
    public ResponseEntity<RecordingDto> resume(@PathVariable String recordingId) {
        try {
            return ResponseEntity.ok(toDto(recordingService.resumeLiveRecording(recordingId)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/{recordingId}/stop")
    public ResponseEntity<RecordingDto> stop(@PathVariable String recordingId) {
        try {
            return ResponseEntity.ok(toDto(recordingService.stopLiveRecording(recordingId)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @GetMapping("/{recordingId}/audio")
    public ResponseEntity<ByteArrayResource> audio(@PathVariable String adventureId, @PathVariable String recordingId) {
        try {
            Recording recording = recordingService.getRecording(recordingId);
            if (recording.audioObjectKey() == null || recording.audioObjectKey().isBlank()) {
                return ResponseEntity.notFound().build();
            }
            byte[] data = audioStore.fetch(recording.audioObjectKey());
            String contentType = guessContentType(recording.audioObjectKey());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(data.length)
                    .body(new ByteArrayResource(data));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private static String guessContentType(String objectKey) {
        String guessed = URLConnection.guessContentTypeFromName(objectKey);
        if (guessed != null) {
            return guessed;
        }
        if (objectKey.endsWith(".webm")) {
            return "audio/webm";
        }
        if (objectKey.endsWith(".wav")) {
            return "audio/wav";
        }
        if (objectKey.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (objectKey.endsWith(".m4a")) {
            return "audio/mp4";
        }
        return "application/octet-stream";
    }

    @GetMapping("/{recordingId}/transcript")
    public List<TranscriptSegmentDto> transcript(@PathVariable String recordingId) {
        return recordingService.getTranscript(recordingId).stream().map(TranscriptSegmentDto::from).toList();
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{recordingId}")
    public ResponseEntity<Void> delete(@PathVariable String recordingId) {
        try {
            recordingService.deleteRecording(recordingId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{recordingId}/transcript")
    public ResponseEntity<RecordingDto> deleteTranscript(@PathVariable String recordingId) {
        try {
            return ResponseEntity.ok(toDto(recordingService.deleteTranscript(recordingId)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    private static RecordingSource parseStartSource(StartRecordingRequest request) {
        if (request == null || request.source() == null || request.source().isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        return RecordingSource.valueOf(request.source().trim().toUpperCase());
    }

    private static String parseDiscordChannelId(RecordingSource source, StartRecordingRequest request) {
        if (source != RecordingSource.DISCORD) {
            return null;
        }
        if (request == null || request.discordChannelId() == null || request.discordChannelId().isBlank()) {
            throw new IllegalArgumentException("discordChannelId is required for Discord recordings");
        }
        return request.discordChannelId().trim();
    }
}
