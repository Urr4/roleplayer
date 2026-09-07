package de.urr4.rp.roleplayer.web;

import de.urr4.rp.roleplayer.domain.port.out.TranscriptionClient;
import de.urr4.rp.roleplayer.domain.port.out.WorldBuildingClient;
import de.urr4.rp.roleplayer.web.dto.ServiceStatusDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes live reachability of the external services roleplayer depends on
 * (WhisperX, Ollama) so the frontend can show a clear status instead of
 * silently hanging, and gate the manual "Retry Transcription"/"Retry fact
 * collection" buttons on whether retrying is actually possible right now.
 */
@RestController
public class ServiceStatusController {

    private final TranscriptionClient transcriptionClient;
    private final WorldBuildingClient worldBuildingClient;

    public ServiceStatusController(TranscriptionClient transcriptionClient, WorldBuildingClient worldBuildingClient) {
        this.transcriptionClient = transcriptionClient;
        this.worldBuildingClient = worldBuildingClient;
    }

    @GetMapping("/api/status")
    public ServiceStatusDto status() {
        return new ServiceStatusDto(transcriptionClient.isReachable(), worldBuildingClient.isReachable());
    }
}
