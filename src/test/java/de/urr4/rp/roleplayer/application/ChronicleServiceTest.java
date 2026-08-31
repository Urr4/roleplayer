package de.urr4.rp.roleplayer.application;

import de.urr4.rp.roleplayer.adapter.memory.InMemoryAdventureRepository;
import de.urr4.rp.roleplayer.adapter.memory.InMemoryCharacterRepository;
import de.urr4.rp.roleplayer.adapter.memory.InMemoryChronicleRepository;
import de.urr4.rp.roleplayer.adapter.memory.InMemoryWorldRepository;
import de.urr4.rp.roleplayer.domain.model.World;
import de.urr4.rp.roleplayer.domain.port.out.ChronicleNpcLinkRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ChronicleServiceTest {
    @Test
    void createChronicleRequiresExistingWorld() {
        ChronicleService service = new ChronicleService(new InMemoryChronicleRepository(), new InMemoryAdventureRepository(),
                mock(AdventureService.class), new InMemoryCharacterRepository(), mock(CharacterService.class),
                new NoopChronicleNpcLinkRepository(), new InMemoryWorldRepository());

        assertThrows(NoSuchElementException.class, () -> service.createChronicle("Test", "missing"));
    }

    @Test
    void createChronicleWithValidWorldSucceeds() {
        InMemoryWorldRepository worldRepository = new InMemoryWorldRepository();
        World world = worldRepository.save(new World("world-1", "Welt", "welt", Instant.now()));
        ChronicleService service = new ChronicleService(new InMemoryChronicleRepository(), new InMemoryAdventureRepository(),
                mock(AdventureService.class), new InMemoryCharacterRepository(), mock(CharacterService.class),
                new NoopChronicleNpcLinkRepository(), worldRepository);

        var chronicle = service.createChronicle("Test", world.id());

        assertEquals(world.id(), chronicle.worldId());
    }

    private static final class NoopChronicleNpcLinkRepository implements ChronicleNpcLinkRepository {
        @Override public void link(String chronicleId, String npcId) {}
        @Override public void unlink(String chronicleId, String npcId) {}
        @Override public List<String> findNpcIdsByChronicle(String chronicleId) { return List.of(); }
        @Override public void unlinkAll(String chronicleId) {}
    }
}
