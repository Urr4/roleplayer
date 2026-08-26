package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.RecordingService;
import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.web.dto.RecordingDto;
import de.urr4.rp.roleplayer.web.dto.StartRecordingRequest;
import de.urr4.rp.roleplayer.web.dto.TranscriptSegmentDto;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/adventures/{adventureId}/recordings")
public class RecordingController {

    private final RecordingService recordingService;

    public RecordingController(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @GetMapping
    public List<RecordingDto> list(@PathVariable String adventureId) {
        return recordingService.listRecordings(adventureId).stream().map(RecordingDto::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RecordingDto> upload(@PathVariable String adventureId, @RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.accepted()
                    .body(RecordingDto.from(recordingService.uploadAndTranscribe(adventureId, file.getOriginalFilename(),
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
            return ResponseEntity.accepted()
                    .body(RecordingDto.from(recordingService.startLiveRecording(adventureId, source, discordChannelId)));
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
            return ResponseEntity.ok(RecordingDto.from(recordingService.pauseLiveRecording(recordingId)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/{recordingId}/resume")
    public ResponseEntity<RecordingDto> resume(@PathVariable String recordingId) {
        try {
            return ResponseEntity.ok(RecordingDto.from(recordingService.resumeLiveRecording(recordingId)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/{recordingId}/stop")
    public ResponseEntity<RecordingDto> stop(@PathVariable String recordingId) {
        try {
            return ResponseEntity.ok(RecordingDto.from(recordingService.stopLiveRecording(recordingId)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @GetMapping("/{recordingId}/transcript")
    public List<TranscriptSegmentDto> transcript(@PathVariable String recordingId) {
        return recordingService.getTranscript(recordingId).stream().map(TranscriptSegmentDto::from).toList();
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
