package de.urr4.rp.roleplayer.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordingKeyFactoryTest {

    @Test
    void createsStableUtcObjectKey() {
        String key = RecordingKeyFactory.create("Session: Alpha / Beta", Instant.parse("2026-08-25T08:15:00Z"),
                Instant.parse("2026-08-25T09:30:05Z"), ".mp3");

        assertEquals("Session_Alpha_Beta/2026-08-25/081500--093005.mp3", key);
    }

    @Test
    void fallsBackToDefaultSessionNameWhenSanitizedNameIsBlank() {
        String key = RecordingKeyFactory.create("***", Instant.parse("2026-08-25T08:15:00Z"),
                Instant.parse("2026-08-25T09:30:05Z"), "wav");

        assertEquals("session/2026-08-25/081500--093005.wav", key);
    }
}
