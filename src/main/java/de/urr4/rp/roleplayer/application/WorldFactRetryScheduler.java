package de.urr4.rp.roleplayer.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorldFactRetryScheduler {
    private static final Logger log = LoggerFactory.getLogger(WorldFactRetryScheduler.class);

    private final WorldFactExtractionService worldFactExtractionService;

    public WorldFactRetryScheduler(WorldFactExtractionService worldFactExtractionService) {
        this.worldFactExtractionService = worldFactExtractionService;
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 30_000)
    public void retryPending() {
        try {
            worldFactExtractionService.retryPending();
        } catch (Exception e) {
            log.error("Failed to retry pending world fact extraction", e);
        }
    }
}
