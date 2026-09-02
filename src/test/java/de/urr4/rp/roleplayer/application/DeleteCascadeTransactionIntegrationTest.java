package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.adapter.memory.InMemoryAudioStore;
import de.urr4.rp.roleplayer.adapter.memory.InMemoryPdfStore;
import de.urr4.rp.roleplayer.adapter.memory.InMemoryTranscriptStore;
import de.urr4.rp.roleplayer.domain.model.Adventure;
import de.urr4.rp.roleplayer.domain.model.AdventureStatus;
import de.urr4.rp.roleplayer.domain.model.Chronicle;
import de.urr4.rp.roleplayer.domain.model.Recording;
import de.urr4.rp.roleplayer.domain.model.RecordingSource;
import de.urr4.rp.roleplayer.domain.model.RecordingStatus;
import de.urr4.rp.roleplayer.domain.model.TranscriptSegment;
import de.urr4.rp.roleplayer.domain.model.World;
import de.urr4.rp.roleplayer.domain.model.WorldExtractionStatus;
import de.urr4.rp.roleplayer.domain.port.out.AdventureRepository;
import de.urr4.rp.roleplayer.domain.port.out.AudioStore;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleRepository;
import de.urr4.rp.roleplayer.domain.port.out.PdfStore;
import de.urr4.rp.roleplayer.domain.port.out.RecordingRepository;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptSegmentRepository;
import de.urr4.rp.roleplayer.domain.port.out.TranscriptStore;
import de.urr4.rp.roleplayer.domain.port.out.WorldRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for a production bug: deleting a Chronicle/Adventure/Recording
 * that actually has persisted {@link TranscriptSegment}s (or other rows removed
 * via a Spring Data JPA *derived* delete query, e.g. {@code deleteByRecordingId})
 * used to fail with
 * {@code jakarta.persistence.TransactionRequiredException: No EntityManager
 * with actual transaction available for current thread - cannot reliably
 * process 'remove' call}. Derived delete queries load matching rows and call
 * {@code EntityManager.remove(...)} on each of them, which - unlike the
 * inherited {@code deleteById(...)} - requires an explicitly active
 * transaction. This only ever failed when there was at least one matching row
 * to remove, which is why it was easy to miss in ad-hoc manual testing against
 * an otherwise-empty database.
 *
 * <p>This test intentionally boots the real Spring context (real JPA
 * repositories, real {@code JpaTransactionManager}, no ambient test
 * transaction) against a throwaway SQLite file, so the missing
 * {@code @Transactional} boundaries that caused the bug are actually
 * exercised - a {@code @DataJpaTest} would silently mask the bug because it
 * wraps every test method in its own transaction.
 */
@SpringBootTest
class DeleteCascadeTransactionIntegrationTest {

    private static Path databaseFile;

    @TestConfiguration
    static class StubStorageConfig {
        @Bean
        AudioStore audioStore() {
            return new InMemoryAudioStore();
        }

        @Bean
        TranscriptStore transcriptStore() {
            return new InMemoryTranscriptStore();
        }

        @Bean
        PdfStore pdfStore() {
            return new InMemoryPdfStore();
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) throws IOException {
        databaseFile = Files.createTempFile("roleplayer-delete-cascade-test", ".db");
        Files.deleteIfExists(databaseFile);
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseFile);
        registry.add("storage.type", () -> "test");
        registry.add("discord.bot-token", () -> "");
    }

    @AfterAll
    static void cleanUpDatabaseFile() throws IOException {
        if (databaseFile != null) {
            Files.deleteIfExists(databaseFile);
        }
    }

    @Autowired
    private WorldRepository worldRepository;
    @Autowired
    private ChronicleRepository chronicleRepository;
    @Autowired
    private AdventureRepository adventureRepository;
    @Autowired
    private RecordingRepository recordingRepository;
    @Autowired
    private TranscriptSegmentRepository transcriptSegmentRepository;
    @Autowired
    private ChronicleService chronicleService;
    @Autowired
    private RecordingService recordingService;

    @Test
    void deletingAChronicleCascadesThroughAdventuresRecordingsAndTranscriptSegments() {
        World world = worldRepository.save(new World(UUID.randomUUID().toString(), "Test World", "test-world",
                Instant.now()));
        Chronicle chronicle = chronicleRepository.save(new Chronicle(UUID.randomUUID().toString(), "Test Chronicle",
                Instant.now(), world.id()));
        Adventure adventure = adventureRepository.save(new Adventure(UUID.randomUUID().toString(), chronicle.id(),
                "Test Adventure", AdventureStatus.COMPLETED, Instant.now(), Instant.now(), Instant.now(),
                WorldExtractionStatus.NONE, null, null));
        Recording recording = recordingRepository.save(new Recording(UUID.randomUUID().toString(), chronicle.id(),
                adventure.id(), RecordingSource.UPLOAD, RecordingStatus.DONE, Instant.now(), Instant.now(),
                "audio-key", "transcript-key", null));
        transcriptSegmentRepository.save(new TranscriptSegment(UUID.randomUUID().toString(), recording.id(),
                "Speaker 1", 0, 1000, "Hallo Welt", Instant.now()));

        assertEquals(1, transcriptSegmentRepository.findByRecordingIdOrderByStartMsAsc(recording.id()).size());

        // Before the fix, this threw TransactionRequiredException as soon as it
        // reached the derived deleteByRecordingId() call, because none of
        // ChronicleService.deleteChronicle/AdventureService.deleteAdventure/
        // RecordingService.deleteRecordingsByAdventureId were @Transactional.
        chronicleService.deleteChronicle(chronicle.id());

        assertTrue(transcriptSegmentRepository.findByRecordingIdOrderByStartMsAsc(recording.id()).isEmpty());
        assertTrue(recordingRepository.findById(recording.id()).isEmpty());
        assertTrue(adventureRepository.findById(adventure.id()).isEmpty());
        assertTrue(chronicleRepository.findById(chronicle.id()).isEmpty());
    }

    @Test
    void deletingARecordingDirectlyRemovesItsTranscriptSegments() {
        World world = worldRepository.save(new World(UUID.randomUUID().toString(), "Test World 2", "test-world-2",
                Instant.now()));
        Chronicle chronicle = chronicleRepository.save(new Chronicle(UUID.randomUUID().toString(), "Test Chronicle 2",
                Instant.now(), world.id()));
        Adventure adventure = adventureRepository.save(new Adventure(UUID.randomUUID().toString(), chronicle.id(),
                "Test Adventure 2", AdventureStatus.COMPLETED, Instant.now(), Instant.now(), Instant.now(),
                WorldExtractionStatus.NONE, null, null));
        Recording recording = recordingRepository.save(new Recording(UUID.randomUUID().toString(), chronicle.id(),
                adventure.id(), RecordingSource.UPLOAD, RecordingStatus.DONE, Instant.now(), Instant.now(),
                "audio-key-2", "transcript-key-2", null));
        transcriptSegmentRepository.save(new TranscriptSegment(UUID.randomUUID().toString(), recording.id(),
                "Speaker 1", 0, 1000, "Servus", Instant.now()));
        transcriptSegmentRepository.save(new TranscriptSegment(UUID.randomUUID().toString(), recording.id(),
                "Speaker 2", 1000, 2000, "Griazi", Instant.now()));

        List<TranscriptSegment> before = transcriptSegmentRepository.findByRecordingIdOrderByStartMsAsc(recording.id());
        assertEquals(2, before.size());

        recordingService.deleteRecording(recording.id());

        assertTrue(transcriptSegmentRepository.findByRecordingIdOrderByStartMsAsc(recording.id()).isEmpty());
        assertTrue(recordingRepository.findById(recording.id()).isEmpty());
    }
}
