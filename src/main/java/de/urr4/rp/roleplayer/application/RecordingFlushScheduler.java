package de.urr4.rp.roleplayer.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecordingFlushScheduler {

    private final LiveRecordingBufferManager liveRecordingBufferManager;

    public RecordingFlushScheduler(LiveRecordingBufferManager liveRecordingBufferManager) {
        this.liveRecordingBufferManager = liveRecordingBufferManager;
    }

    @Scheduled(fixedDelay = 60_000)
    public void flushRecordings() {
        liveRecordingBufferManager.flushRecordingsDue();
    }

    @Scheduled(fixedDelay = 5_000)
    public void refreshDiscordLiveTranscripts() {
        liveRecordingBufferManager.refreshDiscordLiveTranscriptsDue();
    }
}
