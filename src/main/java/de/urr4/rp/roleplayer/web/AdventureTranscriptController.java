package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.application.RecordingService;
import de.urr4.rp.roleplayer.application.TranscriptEventPublisher;
import de.urr4.rp.roleplayer.web.dto.TranscriptSegmentDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/adventures/{adventureId}/transcript")
public class AdventureTranscriptController {

    private final RecordingService recordingService;
    private final TranscriptEventPublisher transcriptEventPublisher;

    public AdventureTranscriptController(RecordingService recordingService,
                                         TranscriptEventPublisher transcriptEventPublisher) {
        this.recordingService = recordingService;
        this.transcriptEventPublisher = transcriptEventPublisher;
    }

    @GetMapping
    public List<TranscriptSegmentDto> transcript(@PathVariable String adventureId) {
        return recordingService.getAdventureTranscript(adventureId).stream().map(TranscriptSegmentDto::from).toList();
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String adventureId) {
        return transcriptEventPublisher.subscribe(adventureId);
    }
}
